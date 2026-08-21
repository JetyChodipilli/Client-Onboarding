package com.brainserve.onboarding.forms.api.request; import jakarta.validation.constraints.Size; public record CreateFormVersionRequest(@Size(max=500) String changeNote){}
