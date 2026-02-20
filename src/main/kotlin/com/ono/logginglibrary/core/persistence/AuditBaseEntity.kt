package com.ono.logginglibrary.core.persistence

import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import java.io.Serializable
import java.time.LocalDateTime

/**
 * Base entity for Spring WebFlux (R2DBC).
 * Uses Spring Data Relational instead of JPA.
 * Requires @EnableR2dbcAuditing in configuration.
 */
abstract class AuditBaseEntity : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    @Id
    var id: Long? = null

    @CreatedDate
    @Column("created_at")
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    @Column("updated_at")
    var updatedAt: LocalDateTime? = null
        protected set

    @CreatedBy
    @Column("created_by")
    var createdBy: String? = null
        protected set

    @LastModifiedBy
    @Column("updated_by")
    var updatedBy: String? = null
        protected set

    @Version
    @Column("version")
    var version: Long? = null

    @Column("is_deleted")
    var isDeleted: Boolean = false

    fun markDeleted() {
        this.isDeleted = true
    }
}