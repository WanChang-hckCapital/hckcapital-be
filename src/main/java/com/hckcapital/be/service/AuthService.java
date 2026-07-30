package com.hckcapital.be.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.hckcapital.be.config.JwtUtil;
import com.hckcapital.be.dto.LoginRequest;
import com.hckcapital.be.dto.LoginResponse;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.model.PasswordResetToken;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.model.Subscription;
import com.hckcapital.be.model.User;
import com.hckcapital.be.repository.MemberRepository;
import com.hckcapital.be.repository.PasswordResetTokenRepository;
import com.hckcapital.be.repository.ProfileRepository;
import com.hckcapital.be.repository.SubscriptionRepository;
import com.hckcapital.be.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    // TODO - session expire token (permanent)
    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SesEmailService sesEmailService;
    private final OtpService otpService;

    // Comma-separated — a Google ID token's own "aud" claim is whichever OAuth Client ID
    // (Web/iOS/Android) actually issued it, and the RN app authenticates against the iOS
    // client (see ../config/api.ts's GOOGLE_IOS_CLIENT_ID), not the Web one the reference
    // Next.js app uses. Accepting a list rather than one fixed audience means both apps'
    // client IDs can share this same backend without either one rejecting the other.
    @Value("${google.client-id}")
    private String googleClientIds;

    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @PostConstruct
    private void initGoogleVerifier() {
        if (googleClientIds == null || googleClientIds.isBlank()) return;
        List<String> audiences = java.util.Arrays.stream(googleClientIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        if (audiences.isEmpty()) return;
        googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();
    }

    @Value("${aws.forgot-password-ses-address:}")
    private String forgotPasswordSesAddress;
    @Value("${app.base-url:}")
    private String appBaseUrl;

    private static final Map<String, String[]> FORGOT_PASSWORD_EMAIL_CONTENT = Map.of(
            // {subject, heading, desc, linkText, expiresMsg} — same copy as the old Next.js
            // reference project's own forget-password route (see hckcapital/app/api/v1/
            // forget-password/route.ts), ported verbatim rather than rewritten.
            "en", new String[]{
                    "flxbubble Reset Password", "Reset Your Password",
                    "Please click the link below to reset your password:",
                    "Reset Password", "This link expires in 10 minutes."
            },
            "zh-TW", new String[]{
                    "flxbubble 重設密碼", "重設您的密碼",
                    "請點擊下方連結重設密碼：",
                    "重設密碼", "此連結將在 10 分鐘後失效。"
            }
    );

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (member.getDeletedAt() != null) {
            throw new RuntimeException("This account has been deleted");
        }

        if (member.getPassword() == null || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        return buildLoginResponse(member);
    }

    /** Verifies the ID token the RN app's own Google Sign-In flow (see
     * useGoogleSignIn.ts) returns, then finds-or-creates the Member/Profile behind that
     * email — mirroring the old Next.js reference's own createOAuthUser, minus the referral
     * system (never ported to this backend at all, see Profile.referralCode's own doc
     * comment) and minus the reference's own bubblePoint/onboarding-bonus bookkeeping
     * (out of scope here — this only needs to authenticate the user, same as any other
     * login). A brand-new account created this way starts unonboarded (onboarded: false),
     * same as a fresh email/password signup would, and has no password set — attempting a
     * regular email/password login on it will correctly fail until one is set. */
    public LoginResponse loginWithGoogle(String idToken) {
        if (googleIdTokenVerifier == null) {
            throw new RuntimeException("Google Sign-In is not configured on this server");
        }

        GoogleIdToken token;
        try {
            token = googleIdTokenVerifier.verify(idToken);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid Google token");
        }
        if (token == null) {
            throw new RuntimeException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new RuntimeException("Google account email is not verified");
        }
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member != null && member.getDeletedAt() != null) {
            throw new RuntimeException("This account has been deleted");
        }
        if (member == null) {
            member = createMemberAndProfileForGoogle(email, name);
        }

        return buildLoginResponse(member);
    }

    private Member createMemberAndProfileForGoogle(String email, String name) {
        Profile profile = new Profile();
        profile.setEmail(email);
        // Falls back to the email's local part when Google doesn't return a display name
        // (rare, but the payload technically allows it) — same "always have something to
        // show" reasoning as this app's own accountname fallbacks elsewhere.
        profile.setAccountname(name != null && !name.isBlank() ? name : email.substring(0, email.indexOf('@')));
        profile.setUsertype("PERSONAL");
        profile.setOnboarded(false);
        Profile savedProfile = profileRepository.save(profile);

        Member member = new Member();
        member.setEmail(email);
        member.setProfiles(List.of(new ObjectId(savedProfile.getId())));
        member.setActiveProfile(0);
        return memberRepository.save(member);
    }

    /** Ported from the old Next.js reference project's own createUser server action (see
     * hckcapital/lib/actions/user.actions.ts) — called only after OtpService.verifyOtp has
     * confirmed the email (see AuthController.signup), same order of operations as the
     * reference, including creating the parallel legacy NextAuth "users" collection
     * document and linking it via Member.user — this backend's own JWT-based auth doesn't
     * read that collection itself, but a Member created here should still be resolvable the
     * same way the reference app's own createUser would leave it, since both stacks share
     * this database (e.g. anything on the reference app's side that still walks
     * Member.user → User). Deliberately still skips:
     * - Referral code redemption (looking up a referrer by refCode, writing a
     *   ReferralHistory row) — never ported to this backend at all, see Profile.
     *   referralCode's own doc comment on that field. A referralCode is still generated and
     *   stored on the new Profile purely because the reference schema's Mongoose model
     *   declares it unique — leaving it null on every signup would risk a duplicate-key
     *   collision against that same index in the shared database.
     * A brand-new account starts unonboarded (onboarded: false), same as loginWithGoogle's
     * own new-account path — the RN app is expected to route into onboarding next. */
    public LoginResponse signup(String email, String username, String password) {
        if (userRepository.findByEmail(email).isPresent() || memberRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("This email is already in use.");
        }
        otpService.requireVerified(email);

        User user = new User();
        user.setEmail(email);
        user.setName(username);
        User savedUser = userRepository.save(user);

        Profile profile = new Profile();
        profile.setEmail(email);
        profile.setAccountname(username);
        profile.setUsertype("PERSONAL");
        profile.setAccountType("PUBLIC");
        profile.setRole("PERSONAL");
        profile.setOnboarded(false);
        profile.setBubblePoint(0);
        profile.setReferralCode(generateReferralCode());
        Profile savedProfile = profileRepository.save(profile);

        Member member = new Member();
        member.setUser(new ObjectId(savedUser.getId()));
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(password));
        member.setProfiles(List.of(new ObjectId(savedProfile.getId())));
        member.setActiveProfile(0);
        member = memberRepository.save(member);

        return buildLoginResponse(member);
    }

    private static final String REFERRAL_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private String generateReferralCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(REFERRAL_CODE_CHARS.charAt(random.nextInt(REFERRAL_CODE_CHARS.length())));
        }
        return code.toString();
    }

    /** Ported from the old Next.js reference project's own POST /api/v1/forget-password route
     * (see hckcapital/app/api/v1/forget-password/route.ts): generates a single-use reset
     * token and emails a link to it. Deliberately does NOT also implement "validate token" /
     * "complete reset" here — the reset link still points at that same reference app's own
     * already-working /reset-password/[token] web page, which reads/writes the very same
     * "passwordresettokens" Mongo collection directly via its own Mongoose model, so it
     * transparently completes resets for tokens created here without any further Java
     * involvement.
     *
     * Silently no-ops for an email with no Member — same generic "if an account exists, a
     * link was sent" response either way (see AuthController) — rather than telling the
     * caller whether that email has an account, which would let anyone probe arbitrary
     * addresses to find out which ones are registered (this used to throw "Fail to find
     * member", a direct user-enumeration bug). */
    /** Settings > Forget Password's own gate — mirrors the old Next.js reference project's
     * own getMemberInfo's `hasPassword` field: false for an OAuth-only account (e.g. signed
     * up via Google, never set a password), in which case that screen shows an "sign in with
     * your original provider instead" notice rather than a password-reset button. */
    public boolean hasPassword(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        return member.getPassword() != null && !member.getPassword().isBlank();
    }

    public void forgotPassword(String email, String lang) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) return;

        String[] content = FORGOT_PASSWORD_EMAIL_CONTENT.getOrDefault(lang, FORGOT_PASSWORD_EMAIL_CONTENT.get("zh-TW"));
        String subject = content[0], heading = content[1], desc = content[2], linkText = content[3], expiresMsg = content[4];

        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        // Every past token for this email — expired or not — is invalidated the moment a new
        // one is requested, so repeatedly tapping "send reset link" can't leave a pile of
        // still-valid tokens behind, and an old reset email a user finds later can't still
        // work once they've requested a newer one.
        passwordResetTokenRepository.deleteByEmail(email);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setMemberId(new ObjectId(member.getId()));
        resetToken.setToken(token);
        resetToken.setEmail(email);
        resetToken.setExpiresAt(expiresAt);
        passwordResetTokenRepository.save(resetToken);

        String resetUrl = appBaseUrl + "/reset-password/" + token;

        if (forgotPasswordSesAddress.isBlank()) {
            throw new RuntimeException("SES configuration missing");
        }

        String htmlContent = "<h3>" + heading + "</h3>"
                + "<p>" + desc + "</p>"
                + "<p><a href=\"" + resetUrl + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + linkText + "</a></p>"
                + "<p>" + expiresMsg + "</p>";

        try {
            sesEmailService.sendHtml(forgotPasswordSesAddress, email, subject, htmlContent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send reset link");
        }
    }

    private LoginResponse buildLoginResponse(Member member) {
        List<ObjectId> profileObjIds = member.getProfiles();
        int activeIndex = member.getActiveProfile();
        String profileId = (profileObjIds != null && !profileObjIds.isEmpty() && activeIndex < profileObjIds.size())
                ? profileObjIds.get(activeIndex).toHexString()
                : null;

        String name = null;
        String userType = "PERSONAL";
        boolean onboarded = false;
        boolean hasSubscription = false;
        String profileImage = null;

        if (profileId != null) {
            Profile profile = profileRepository.findById(profileId).orElse(null);
            if (profile != null) {
                name = profile.getAccountname();
                userType = profile.getUsertype() != null ? profile.getUsertype() : "PERSONAL";
                onboarded = Boolean.TRUE.equals(profile.getOnboarded());
                profileImage = profile.getImageFilePath();

                List<ObjectId> subObjIds = profile.getSubscription();
                if (subObjIds != null && !subObjIds.isEmpty()) {
                    List<String> subIds = subObjIds.stream().map(ObjectId::toHexString).toList();
                    List<Subscription> subs = subscriptionRepository.findAllByIdIn(subIds);
                    LocalDateTime now = LocalDateTime.now();
                    hasSubscription = subs.stream().anyMatch(sub ->
                            "active".equals(sub.getStatus()) &&
                            sub.getEstimatedEndDate() != null &&
                            sub.getEstimatedEndDate().isAfter(now)
                    );
                }
            }
        }

        member.setLastlogin(LocalDateTime.now());
        memberRepository.save(member);

        String token = jwtUtil.generateToken(member.getId(), member.getEmail());
        return new LoginResponse(token, member.getId(), member.getEmail(), profileId, name, userType, onboarded, hasSubscription, profileImage);
    }
}
