package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import com.raulshma.jellyplay.core.database.crypto.TokenCipher

/**
 * The complete, correctly-ordered v1→v57 migration chain, with the
 * token-encrypting [Migration24To25] (which needs a [TokenCipher]) and the
 * container-backfilling [Migration53To54] (which needs a [ContainerProbe])
 * as constructor-injected steps at their true positions. Room matches
 * migrations by start/end version regardless of list order, but keeping the
 * chain in strict ascending order here makes the source a reliable map of
 * the upgrade path and lets [MigrationTest] assert contiguity.
 */
fun allMigrations(
    tokenCipher: TokenCipher,
    containerProbe: ContainerProbe,
): List<Migration> =
    listOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        Migration24To25(tokenCipher),
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44,
        MIGRATION_44_45,
        MIGRATION_45_46,
        MIGRATION_46_47,
        MIGRATION_47_48,
        MIGRATION_48_49,
        MIGRATION_49_50,
        MIGRATION_50_51,
        MIGRATION_51_52,
        MIGRATION_52_53,
        Migration53To54(containerProbe),
        MIGRATION_54_55,
        MIGRATION_55_56,
        MIGRATION_56_57,
    )
