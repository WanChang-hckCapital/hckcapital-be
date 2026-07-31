package com.hckcapital.be.service;

import com.hckcapital.be.dto.AdminOverviewResponse;
import com.hckcapital.be.dto.DailySeriesPointResponse;
import com.hckcapital.be.dto.MemberCountryBreakdownResponse;
import com.hckcapital.be.dto.MemberDetailResponse;
import com.hckcapital.be.dto.MemberListResponse;
import com.hckcapital.be.dto.MemberSummaryResponse;
import com.hckcapital.be.model.Member;
import com.hckcapital.be.model.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dashboard > Dashboard (overview) — ported from the old Next.js reference's own
 * app/[lang]/(dashboard)/dashboard/page.tsx + lib/actions/admin.actions.ts. See
 * AdminOverviewResponse for how each field maps back to the reference. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final MongoTemplate mongoTemplate;
    private final StripeService stripeService;

    public AdminOverviewResponse getOverview(LocalDate startDate, LocalDate endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneWeekAgo = now.minusDays(7);
        LocalDateTime twoWeeksAgo = now.minusDays(14);
        double weeklyRevenue = sumSubscriptionRevenue(oneWeekAgo, now);
        double lastWeekRevenue = sumSubscriptionRevenue(twoWeeksAgo, oneWeekAgo);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfThisMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfLastMonth = today.minusMonths(1).withDayOfMonth(1).atStartOfDay();
        double monthlyRevenue = sumSubscriptionRevenue(startOfThisMonth, now);
        double lastMonthRevenue = sumSubscriptionRevenue(startOfLastMonth, startOfThisMonth);

        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = endDate.atTime(23, 59, 59);

        List<DailySeriesPointResponse> newMembersSeries = newMembersByDay(startDate, endDate, rangeStart, rangeEnd);
        List<DailySeriesPointResponse> cumulativeMembersSeries = cumulativeCountByDay(
                "members", "createdAt", null, startDate, endDate);

        List<DailySeriesPointResponse> cumulativePersonalSeries = cumulativeCountByDay(
                "profiles", "createdAt", "PERSONAL", startDate, endDate);
        List<DailySeriesPointResponse> cumulativeOrganizationSeries = cumulativeCountByDay(
                "profiles", "createdAt", "ORGANIZATION", startDate, endDate);

        StripeService.ActiveSubscriptionsSeriesResult subsResult =
                stripeService.getActiveSubscriptionsSeries(startDate, endDate);

        List<MemberCountryBreakdownResponse> membersByCountry = getMembersByCountry();

        return new AdminOverviewResponse(
                weeklyRevenue, lastWeekRevenue, calcGrowth(weeklyRevenue, lastWeekRevenue),
                monthlyRevenue, lastMonthRevenue, calcGrowth(monthlyRevenue, lastMonthRevenue),
                newMembersSeries, cumulativeMembersSeries,
                cumulativePersonalSeries, cumulativeOrganizationSeries,
                subsResult.configured(), subsResult.errorMessage(), subsResult.series(),
                membersByCountry
        );
    }

    private double calcGrowth(double current, double previous) {
        if (previous == 0) return current > 0 ? 100 : 0;
        return ((current - previous) / previous) * 100;
    }

    private double sumSubscriptionRevenue(LocalDateTime start, LocalDateTime end) {
        Criteria match = Criteria.where("planStarted").gte(start).lte(end);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(match),
                Aggregation.group().sum("totalAmount").as("total")
        );
        Document result = mongoTemplate.aggregate(aggregation, "subscriptions", Document.class)
                .getUniqueMappedResult();
        return result != null ? result.get("total", Number.class).doubleValue() : 0;
    }

    /** New members registered per day — Member.createdAt bucketed by day, not cumulative. */
    private List<DailySeriesPointResponse> newMembersByDay(
            LocalDate startDate, LocalDate endDate, LocalDateTime rangeStart, LocalDateTime rangeEnd
    ) {
        List<Member> members = mongoTemplate.find(
                Query.query(Criteria.where("createdAt").gte(rangeStart).lte(rangeEnd)), Member.class);
        Map<String, Integer> countsByDay = new HashMap<>();
        for (Member m : members) {
            if (m.getCreatedAt() == null) continue;
            String key = m.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            countsByDay.merge(key, 1, Integer::sum);
        }
        List<DailySeriesPointResponse> series = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            String key = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
            series.add(new DailySeriesPointResponse(key, countsByDay.getOrDefault(key, 0)));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /** Cumulative document count by day for a given collection/date field, optionally
     * scoped to a Profile.usertype value — one point per day in [startDate, endDate],
     * seeded with however many matching documents already existed before startDate so the
     * first plotted point is the real running total, not just "new since the range
     * began." Same seed-then-walk shape as ProfileService.getFollowersSeries. */
    private List<DailySeriesPointResponse> cumulativeCountByDay(
            String collection, String dateField, String usertype, LocalDate startDate, LocalDate endDate
    ) {
        Criteria beforeRange = Criteria.where(dateField).lt(startDate.atStartOfDay());
        if (usertype != null) beforeRange.and("usertype").is(usertype);
        long seed = mongoTemplate.count(Query.query(beforeRange), collection);

        Criteria inRange = Criteria.where(dateField).gte(startDate.atStartOfDay()).lte(endDate.atTime(23, 59, 59));
        if (usertype != null) inRange.and("usertype").is(usertype);
        List<Document> docs = mongoTemplate.find(Query.query(inRange), Document.class, collection);

        Map<String, Integer> newByDay = new HashMap<>();
        for (Document doc : docs) {
            Object rawDate = doc.get(dateField);
            if (!(rawDate instanceof java.util.Date date)) continue;
            String key = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
            newByDay.merge(key, 1, Integer::sum);
        }

        List<DailySeriesPointResponse> series = new ArrayList<>();
        long cumulative = seed;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            String key = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE);
            cumulative += newByDay.getOrDefault(key, 0);
            series.add(new DailySeriesPointResponse(key, (int) cumulative));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    private List<MemberCountryBreakdownResponse> getMembersByCountry() {
        List<Member> members = mongoTemplate.findAll(Member.class);
        Map<String, Integer> countsByCountry = new HashMap<>();
        for (Member m : members) {
            String country = (m.getCountry() == null || m.getCountry().isBlank()) ? "Unknown" : m.getCountry();
            countsByCountry.merge(country, 1, Integer::sum);
        }
        int total = members.size();
        List<MemberCountryBreakdownResponse> breakdown = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : countsByCountry.entrySet()) {
            double percentage = total > 0 ? Math.round((entry.getValue() * 1000.0) / total) / 10.0 : 0;
            breakdown.add(new MemberCountryBreakdownResponse(entry.getKey(), entry.getValue(), percentage));
        }
        breakdown.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        return breakdown;
    }

    /** Dashboard > Member's list — see MemberSummaryResponse. Ported from the old Next.js
     * reference's own fetchProfilesPaginated: `type` narrows by usertype ("general" =
     * PERSONAL/ORGANIZATION, "admin" = FLEXADMIN, anything else = unfiltered), `search`
     * case-insensitively matches accountname OR email, newest-first. */
    public MemberListResponse getMemberList(int page, int limit, String type, String search) {
        Criteria criteria = new Criteria();
        List<Criteria> clauses = new ArrayList<>();

        if ("general".equals(type)) {
            clauses.add(Criteria.where("usertype").in("PERSONAL", "ORGANIZATION"));
        } else if ("admin".equals(type)) {
            clauses.add(Criteria.where("usertype").is("FLEXADMIN"));
        }
        if (search != null && !search.isBlank()) {
            clauses.add(new Criteria().orOperator(
                    Criteria.where("accountname").regex(search, "i"),
                    Criteria.where("email").regex(search, "i")
            ));
        }
        if (!clauses.isEmpty()) {
            criteria.andOperator(clauses.toArray(new Criteria[0]));
        }

        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) Math.max(0, page - 1) * limit)
                .limit(limit);
        List<Profile> profiles = mongoTemplate.find(query, Profile.class);
        long total = mongoTemplate.count(Query.query(criteria), Profile.class);

        List<MemberSummaryResponse> data = new ArrayList<>();
        for (Profile p : profiles) {
            data.add(new MemberSummaryResponse(
                    p.getId(), p.getAccountname(), p.getEmail(), p.getImageFilePath(),
                    p.getUsertype(), p.getCards() != null ? p.getCards().size() : 0,
                    p.getOnboarded(), p.getCreatedAt()
            ));
        }
        return new MemberListResponse(data, (int) total);
    }

    /** Dashboard > Member's detail sheet — see MemberDetailResponse. Ported from the old
     * Next.js reference's own fetchProfileDetails: everything but `country` comes off the
     * Profile document itself; `country` is a reverse lookup onto whichever Member has this
     * profile in its own `profiles` array (Profile has no country field of its own). */
    public MemberDetailResponse getMemberDetail(String profileId) {
        Profile profile = mongoTemplate.findById(profileId, Profile.class);
        if (profile == null) {
            throw new IllegalArgumentException("Profile not found");
        }

        Member member = mongoTemplate.findOne(
                Query.query(Criteria.where("profiles").is(new ObjectId(profileId))), Member.class);

        return new MemberDetailResponse(
                profile.getUsertype(), profile.getAccountType(), profile.getOnboarded(),
                profile.getReferralCode(), profile.getBubblePoint() != null ? profile.getBubblePoint() : 0,
                member != null ? member.getCountry() : null, profile.getStripeCustomerId()
        );
    }
}
