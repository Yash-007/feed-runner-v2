# Enums looked up by name from server strings (PostStyle.valueOf, Angle.valueOf
# and friends): keep their constant names so valueOf keeps working after R8.
-keepclassmembers enum com.yash.feedrunner.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
