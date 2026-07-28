package com.hckcapital.be.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Spring Data's equivalent of Mongoose's {timestamps: true} (see the old Next.js reference
 * project's own member.ts/profile.ts schemas, both declared with it) — @CreatedDate/
 * @LastModifiedDate on Member/Profile only actually populate themselves with this enabled. */
@Configuration
@EnableMongoAuditing
public class MongoAuditingConfig {
}
