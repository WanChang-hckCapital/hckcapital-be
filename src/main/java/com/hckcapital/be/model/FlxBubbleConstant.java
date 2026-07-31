package com.hckcapital.be.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Generic key/value settings collection — mirrors the old Next.js reference project's own
 * lib/models/flxbubble-constants.ts (same collection, "flxbubbleconstants", Mongoose's
 * default pluralized name for model "FlxBubbleConstant"). Only `key` "BOOLEAN_SHOW_FREE_PLAN"
 * is read anywhere in this backend so far — see StripeService.shouldShowFreePlan. `value` is
 * genuinely mixed-type in Mongo (string/number/boolean/object/array depending on the key),
 * so it stays Object here rather than a narrower Java type. */
@Data
@Document(collection = "flxbubbleconstants")
public class FlxBubbleConstant {
    @Id
    private String id;

    private String key;

    private Object value;

    private String description;
}
