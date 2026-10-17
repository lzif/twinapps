package io.github.lzif.twinapps.clone

/**
 * A destination profile shown by the clone destination chooser.
 *
 * Spec isl.clone.ui: the chooser lists the parent profile ("Mainland") plus
 * every Island (work profile). Profile naming sync is deferred (spec
 * isl.profile.named-islands), so Islands get generic names for now.
 */
data class CloneTarget(
    /** Android user id of the destination profile. */
    val userId: Int,
    /** Display name shown in the chooser. */
    val displayName: String,
    /** True for the parent profile entry. */
    val isParentProfile: Boolean
)
