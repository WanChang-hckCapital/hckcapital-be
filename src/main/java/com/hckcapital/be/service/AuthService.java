package com.hckcapital.be.service;

import com.hckcapital.be.config.JwtUtil;
import com.hckcapital.be.dto.LoginRequest;
import com.hckcapital.be.dto.LoginResponse;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.model.Profile;
import com.hckcapital.be.model.Subscription;
import com.hckcapital.be.repository.MemberRepository;
import com.hckcapital.be.repository.ProfileRepository;
import com.hckcapital.be.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (member.getDeletedAt() != null) {
            throw new RuntimeException("This account has been deleted");
        }

        if (member.getPassword() == null || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

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
