package com.javierrb35.flashlight;
import org.json.JSONArray;
import org.json.JSONObject;

final class UpdateRelease {
    final String tag, name, title, notes, url, digest, assetName;
    final int code;
    final long id, size;

    private UpdateRelease(String tag,String name,String title,String notes,String url,
            String digest,String assetName,int code,long id,long size){
        this.tag=tag;this.name=name;this.title=title;this.notes=notes;
        this.url=url;this.digest=digest;this.assetName=assetName;
        this.code=code;this.id=id;this.size=size;
    }

    static UpdateRelease parse(JSONObject release){
        if(release==null||release.optBoolean("draft")||release.optBoolean("prerelease"))return null;
        String tag=release.optString("tag_name","");
        ReleaseVersions version=ReleaseVersions.parse(tag);
        if(version==null||!UpdateAssetRules.sameUrl(
                UpdateAssetRules.RELEASE_PREFIX+tag,release.optString("html_url","")))return null;
        JSONArray assets=release.optJSONArray("assets");
        if(assets==null)return null;
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.optJSONObject(i);
            if(a==null||!"uploaded".equals(a.optString("state","uploaded")))continue;
            long id=a.optLong("id"),size=a.optLong("size");
            String name=a.optString("name",""),hash=a.optString("digest","");
            String url=a.optString("browser_download_url","");
            if(!UpdateAssetRules.valid(tag,id,size,name,hash,url))continue;
            String title=release.optString("name",tag).trim();
            String notes=release.optString("body","").trim();
            if(notes.length()>350)notes=notes.substring(0,349).trim()+"…";
            return new UpdateRelease(tag,version.versionName(),title,notes,url,hash,
                    name,version.build,id,size);
        }
        return null;
    }

    JSONObject toJson() throws org.json.JSONException{
        return new JSONObject().put("tag",tag).put("name",name).put("title",title)
                .put("notes",notes).put("url",url).put("digest",digest)
                .put("assetName",assetName).put("code",code).put("id",id).put("size",size);
    }

    static UpdateRelease fromCache(String raw){
        try{
            JSONObject o=new JSONObject(raw);
            String tag=o.getString("tag"),asset=o.getString("assetName");
            String url=o.getString("url"),digest=o.getString("digest");
            int code=o.getInt("code");
            long id=o.getLong("id"),size=o.getLong("size");
            ReleaseVersions version=ReleaseVersions.parse(tag);
            if(version==null||version.build!=code||!version.versionName().equals(o.getString("name"))||
                    !UpdateAssetRules.valid(tag,id,size,asset,digest,url))return null;
            return new UpdateRelease(tag,version.versionName(),o.optString("title",tag),
                    o.optString("notes",""),url,digest,asset,code,id,size);
        }catch(Exception ignored){return null;}
    }
}
