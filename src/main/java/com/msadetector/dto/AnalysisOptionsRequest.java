package com.msadetector.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Optional per-analysis detector configuration. Null fields keep the
 * application defaults stored on the newly created AnalysisJob.
 */
public record AnalysisOptionsRequest(
        Boolean runDesignite,
        Boolean detectCyclicDependencies,
        Boolean detectSharedDatabases,
        Boolean detectNanoServices,
        Boolean detectGodServices,
        Boolean detectChattyServices,
        Boolean detectHardcodedEndpoints,
        Boolean detectDistributedMonoliths,
        Boolean detectApiVersioningAbsence,
        Boolean detectWrongCuts,
        Boolean detectEsbMisuse,

        @Positive Integer nanoServiceMaxLoc,
        @PositiveOrZero Integer nanoServiceMaxEndpoints,
        @Positive Integer chattyServiceMinCalls,

        @Positive Integer godServiceFieldCount,
        @Positive Integer godServicePublicMethods,
        @Positive Integer godServiceLoc,
        @Positive Integer godServiceImportDomains,
        @Positive Integer godServiceConstructorParams,
        @DecimalMin("0.0") @DecimalMax("1.0") Double godServiceTccThreshold,
        @Positive Integer godServiceMinMetrics,

        @DecimalMin("0.0") @DecimalMax("1.0") Double esbMediatorThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") Double distributedMonolithHighCoupling,
        @DecimalMin("0.0") @DecimalMax("1.0") Double distributedMonolithConnectedRatio,
        @DecimalMin("0.0") @DecimalMax("1.0") Double distributedMonolithModerateCoupling
) {}
