package com.brainserve.clientonboarding.assets.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssetPolicyTest {
    @Test void filenamesCannotBecomePathsOrResponseHeaders(){
        assertThat(AssetPolicy.filename("../../brand\r\nContent-Type: text/html.svg")).doesNotContain("/","\\","\r","\n",":","..");
        assertThat(AssetPolicy.filename("x".repeat(500))).hasSize(180);
        assertThatThrownBy(()->AssetPolicy.filename(" ")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void requirementsRejectActiveContentUnknownTypesNullsAndUnboundedSizes(){
        for(var types:List.of(List.of("text/html"),List.of("image/svg+xml"),Arrays.asList("text/plain",null),List.of("text/plain","text/plain")))
            assertThatThrownBy(()->AssetPolicy.requirement(types,100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AssetPolicy.requirement(List.of("text/plain"),AssetPolicy.MAX_BYTES+1)).isInstanceOf(IllegalArgumentException.class);
        AssetPolicy.requirement(List.of("text/plain","application/pdf"),AssetPolicy.MAX_BYTES);
    }
}
