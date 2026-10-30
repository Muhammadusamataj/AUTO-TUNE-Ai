import com.autotuneeq.mvp.TrackIdentity;
import java.io.*; import java.util.Base64;

/** Reads "base64(title) base64(artist)" lines, prints "base64(canonicalTitle) base64(canonicalArtist)". Test helper for identity_parity_fuzz.py. */
public class IdentityDump {
    public static void main(String[] a) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        PrintStream o = new PrintStream(System.out, true, "UTF-8"); String l;
        Base64.Decoder d = Base64.getDecoder(); Base64.Encoder e = Base64.getEncoder();
        while ((l = r.readLine()) != null) {
            String[] p = l.split(" ", -1);
            String t = new String(d.decode(p[0]), "UTF-8"), ar = new String(d.decode(p[1]), "UTF-8");
            o.println(e.encodeToString(TrackIdentity.canonicalTitle(t, ar).getBytes("UTF-8")) + " "
                    + e.encodeToString(TrackIdentity.canonicalArtist(ar).getBytes("UTF-8")));
        }
    }
}
