package com.hckcapital.be.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.hckcapital.be.config.JwtUtil;
import com.hckcapital.be.dto.LoginRequest;
import com.hckcapital.be.dto.LoginResponse;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.model.Subscription;
import com.hckcapital.be.repository.MemberRepository;
import com.hckcapital.be.repository.ProfileRepository;
import com.hckcapital.be.repository.SubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    // TODO - session expire token (permanent)
    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
