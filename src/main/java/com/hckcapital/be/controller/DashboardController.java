package com.hckcapital.be.controller;

import com.hckcapital.be.dto.AdminOverviewResponse;
import com.hckcapital.be.dto.AffiliatesOverviewResponse;
import com.hckcapital.be.dto.MemberListResponse;
import com.hckcapital.be.dto.ProductsOverviewResponse;
import com.hckcapital.be.dto.PromotionsOverviewResponse;
import com.hckcapital.be.dto.TransactionsOverviewResponse;
import com.hckcapital.be.service.AdminService;
import com.hckcapital.be.service.RewardfulService;
import com.hckcapital.be.service.StripeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final RewardfulService rewardfulService;
    private final StripeService stripeService;
    private final AdminService adminService;

    // See AdminService.getOverview — DashboardScreen.tsx's own "overview" (Dashboard) menu
    // item. Period-scoped like /transactions — see AdminOverviewResponse's own doc comment
    // on which fields that range actually applies to.
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        try {
            return ResponseEntity.ok(adminService.getOverview(
                    LocalDate.parse(startDate), LocalDate.parse(endDate)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid startDate/endDate"));
        }
    }

    // See AdminService.getMemberList/getMemberDetail — DashboardScreen.tsx's own Member
    // menu item. `type` is "all" | "general" | "admin", same three options as the
    // reference's own fetchProfilesPaginated; omitted/anything else means unfiltered.
    @GetMapping("/members")
    public ResponseEntity<MemberListResponse> getMembers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "") String search
    ) {
        return ResponseEntity.ok(adminService.getMemberList(page, limit, type, search));
    }

    @GetMapping("/members/{profileId}")
    public ResponseEntity<?> getMemberDetail(@PathVariable String profileId) {
        try {
            return ResponseEntity.ok(adminService.getMemberDetail(profileId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Member not found"));
        }
    }

    // See RewardfulService.getAffiliatesOverview — DashboardScreen.tsx's own Affiliates
    // menu item.
    @GetMapping("/affiliates")
    public ResponseEntity<AffiliatesOverviewResponse> getAffiliates() {
        return ResponseEntity.ok(rewardfulService.getAffiliatesOverview());
    }

    // See StripeService.getProductsOverview — DashboardScreen.tsx's own Product menu item.
    @GetMapping("/products")
    public ResponseEntity<ProductsOverviewResponse> getProducts() {
        return ResponseEntity.ok(stripeService.getProductsOverview());
    }

    // See StripeService.getPromotionsOverview — DashboardScreen.tsx's own Promotion menu item.
    @GetMapping("/promotions")
    public ResponseEntity<PromotionsOverviewResponse> getPromotions() {
        return ResponseEntity.ok(stripeService.getPromotionsOverview());
    }

    // See StripeService.getTransactionsOverview — DashboardScreen.tsx's own Transaction menu
    // item. Unlike the other three Dashboard endpoints, this one is period-scoped by
    // TransactionsSection.tsx's own PeriodPicker — both bounds are required (a stats/chart
    // screen has no meaningful "all time" fallback the way a plain list does).
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        try {
            return ResponseEntity.ok(stripeService.getTransactionsOverview(
                    LocalDate.parse(startDate), LocalDate.parse(endDate)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid startDate/endDate"));
        }
    }
}
