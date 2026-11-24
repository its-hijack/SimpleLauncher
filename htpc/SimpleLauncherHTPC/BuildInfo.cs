namespace SimpleLauncherHTPC
{
    /// <summary>Version details. The release workflow fills these in from htpc/version.txt and the repo's secrets.</summary>
    static class BuildInfo
    {
        public const string Release = "V1";
        public const string BuildTag = "241126-1.0";
        /// <summary>SteamGridDB key for artwork, from the SGDB_API_KEY secret. Empty in local builds.</summary>
        public const string SgdbKey = "";
        public const string Repo = "its-hijack/SimpleLauncher";
        public const string TagPrefix = "htpc-";
    }
}
