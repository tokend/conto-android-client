package org.tokend.template.data.model

class CompanyRecord(
        val id: String,
        val name: String,
        val logoUrl: String?
) {
    override fun equals(other: Any?): Boolean {
        return other is CompanyRecord && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}