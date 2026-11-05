package com.javierrb35.flashlight;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateReleaseTest {
    private static JSONObject release() throws Exception {
        String tag="v1.1.1-build.3";
        String name="FlashLight-1.1.1-build.3-release.apk";
        JSONObject asset=new JSONObject().put("id",987).put("size",100000)
                .put("name",name).put("state","uploaded")
                .put("digest","sha256:"+"b".repeat(64))
                .put("browser_download_url",UpdateAssetRules.ASSET_PREFIX+tag+"/"+name);
        return new JSONObject().put("tag_name",tag)
                .put("html_url",UpdateAssetRules.RELEASE_PREFIX+tag)
                .put("name","FlashLight 1.1.1").put("body","Correcciones y mejoras.")
                .put("draft",false).put("prerelease",false)
                .put("assets",new JSONArray().put(asset));
    }

    @Test public void acceptsOnlyVerifiableReleaseAndPersistsValidatedCache() throws Exception {
        UpdateRelease result=UpdateRelease.parse(release());
        assertNotNull(result);
        assertEquals(3,result.code);
        assertEquals("1.1.1",result.name);
        UpdateRelease restored=UpdateRelease.fromCache(result.toJson().toString());
        assertNotNull(restored);
        assertEquals(result.digest,restored.digest);
        assertNull(UpdateRelease.fromCache(result.toJson()
                .put("url","https://evil.example/release.apk").toString()));
    }

    @Test public void doesNotAcceptDraftsPreReleasesOrAssetsWithoutHash() throws Exception{
        assertNull(UpdateRelease.parse(release().put("draft",true)));
        assertNull(UpdateRelease.parse(release().put("prerelease",true)));
        JSONObject noHash=release();
        noHash.getJSONArray("assets").getJSONObject(0).remove("digest");
        assertNull(UpdateRelease.parse(noHash));
    }
}
