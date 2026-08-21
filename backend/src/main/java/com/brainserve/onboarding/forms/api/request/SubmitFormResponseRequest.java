package com.brainserve.onboarding.forms.api.request; import jakarta.validation.constraints.PositiveOrZero; public record SubmitFormResponseRequest(@PositiveOrZero long version){}
