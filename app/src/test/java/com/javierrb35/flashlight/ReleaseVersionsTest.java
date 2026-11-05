package com.javierrb35.flashlight;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReleaseVersionsTest {
    @Test public void strictlyComparesSemanticAndAndroidBuildVersions() {
        assertTrue(ReleaseVersions.newer("v1.1.1-build.3","1.1.0",2));
        assertTrue(ReleaseVersions.newer("v1.1.0-build.3","1.1.0",2));
        assertFalse(ReleaseVersions.newer("v1.1.0-build.2","1.1.0",2));
        assertFalse(ReleaseVersions.newer("v1.0.9-build.10","1.1.0",2));
        assertFalse(ReleaseVersions.newer("v1.2.0-build.1","1.1.0",2));
    }

    @Test public void malformedAndOverflowedVersionsNeverBecomeUpdates(){
        for(String invalid : new String[]{"","latest","v1.2","v1.2.3-beta","1.2.3.apk",
                "v99999.1.2","v1.2.3-build.9999999999","v1.2.3-build.-1"}) {
            assertNull(invalid,ReleaseVersions.parse(invalid));
        }
        assertEquals("1.2.3",ReleaseVersions.parse("v1.2.3-build.4").versionName());
    }
}
