using System;
using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;

namespace SimpleLauncherHTPC
{
    /// <summary>Shared web access.</summary>
    static class Net
    {
        public static readonly HttpClient Http = Make();

        static HttpClient Make()
        {
            var h = new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
            h.DefaultRequestHeaders.UserAgent.Add(new ProductInfoHeaderValue("SimpleLauncherHTPC", "1.0"));
            return h;
        }

        public static async Task<string> GetString(string url, string bearer = null, int timeoutSeconds = 15)
        {
            using (var req = new HttpRequestMessage(HttpMethod.Get, url))
            using (var cts = new System.Threading.CancellationTokenSource(TimeSpan.FromSeconds(timeoutSeconds)))
            {
                if (bearer != null) req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", bearer);
                using (var res = await Http.SendAsync(req, cts.Token))
                {
                    if (!res.IsSuccessStatusCode) throw new HttpRequestException("HTTP " + (int)res.StatusCode);
                    return await res.Content.ReadAsStringAsync();
                }
            }
        }

        /// <summary>Downloads a file to the folder, named after the key, and returns its path.</summary>
        public static async Task<string> Download(string url, string folder, string key)
        {
            string ext = Path.GetExtension(new Uri(url).AbsolutePath);
            if (string.IsNullOrEmpty(ext) || ext.Length > 5) ext = ".png";
            string path = Path.Combine(folder, Hash(key) + ext);
            byte[] data = await Http.GetByteArrayAsync(url);
            File.WriteAllBytes(path, data);
            return path;
        }

        public static string Hash(string s)
        {
            using (var sha = SHA1.Create())
            {
                var b = sha.ComputeHash(Encoding.UTF8.GetBytes(s));
                var sb = new StringBuilder();
                for (int i = 0; i < 8; i++) sb.Append(b[i].ToString("x2"));
                return sb.ToString();
            }
        }
    }
}
