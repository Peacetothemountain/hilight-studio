-keep class com.hilight.core.** { *; }
-keep class com.hilight.studio.HiLightUserService {
    public <init>();
    public *;
}
-keep interface com.hilight.core.IHiLightService* { *; }
-keep class com.hilight.core.IHiLightService* { *; }
