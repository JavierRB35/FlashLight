package com.javierrb35.flashlight;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateAssetRulesTest {
    private static final String TAG="v1.1.1-build.3";
    private static final String NAME="FlashLight-1.1.1-build.3-release.apk";
    private static final String SHA="sha256:"+"a".repeat(64);
    private static final String URL=UpdateAssetRules.ASSET_PREFIX+TAG+"/"+NAME;

    @Test public void acceptsOnlyOfficialApkWithMatchingDigest(){
        assertTrue(UpdateAssetRules.valid(TAG,123L,120000L,NAME,SHA,URL));
        assertFalse(UpdateAssetRules.valid(TAG,123L,120000L,NAME,null,URL));
        assertFalse(UpdateAssetRules.valid(TAG,123L,0L,NAME,SHA,URL));
        assertFalse(UpdateAssetRules.valid(TAG,123L,120000L,"FlashLight-debug.apk",SHA,URL));
        assertFalse(UpdateAssetRules.valid(TAG,123L,120000L,NAME,SHA,
                URL.replace("github.com","evil.example")));
        assertFalse(UpdateAssetRules.valid(TAG,123L,120000L,NAME,SHA,URL+"?redirect=1"));
        assertFalse(UpdateAssetRules.valid("v1.1.1",123L,120000L,NAME,SHA,URL));
        assertFalse(UpdateAssetRules.valid(TAG,123L,UpdateAssetRules.MAX_BYTES+1,NAME,SHA,URL));
        assertEquals("a".repeat(64),UpdateAssetRules.digestHex(SHA));
    }

    @Test public void restrictsDownloadRedirectsToGithubContent(){
        assertTrue(UpdateAssetRules.trustedRedirectHost("release-assets.githubusercontent.com"));
        assertTrue(UpdateAssetRules.trustedRedirectHost("github.com"));
        assertFalse(UpdateAssetRules.trustedRedirectHost("github.com.evil.example"));
        assertFalse(UpdateAssetRules.trustedRedirectHost("evilgithubusercontent.com"));
    }
}
