// SPDX-License-Identifier: GPL-3.0-only
package dev.opd2515.audiomanager;

import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

final class RootStore {
  static final String STOCK = "/system_ext/etc/Multimedia_Daemon_List.xml",
      DEST = "/data/oplus/multimedia/Multimedia_Daemon_Online_List.xml",
      STATE = "/data/oplus/multimedia/opd2515-audio-manager";
  String stock, current, stockHash, currentHash;

  static String run(String script) throws Exception {
    Process p = new ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Thread reader =
        new Thread(
            () -> {
              try {
                byte[] b = new byte[8192];
                int n;
                while ((n = p.getInputStream().read(b)) != -1) {
                  if (out.size() < 24 * 1024 * 1024) out.write(b, 0, n);
                }
              } catch (IOException ignored) {
              }
            });
    reader.start();
    IOException inputError = null;
    try (OutputStream input = p.getOutputStream()) {
      input.write((script + "\n").getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      inputError = e;
    }
    if (!p.waitFor(45, TimeUnit.SECONDS)) {
      p.destroy();
      throw new IOException("Root operation timed out; reload before retrying");
    }
    reader.join(2000);
    String result = out.toString("UTF-8").trim();
    if (p.exitValue() != 0)
      throw new IOException(result.isEmpty() ? "Root denied or operation failed" : result);
    if (inputError != null)
      throw new IOException(
          result.isEmpty() ? "Root input failed; reload before retrying" : result, inputError);
    return result;
  }

  static String hash(String value) throws Exception {
    StringBuilder s = new StringBuilder();
    for (byte b :
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
      s.append(String.format("%02x", b & 255));
    return s.toString();
  }

  static String guards() {
    return "set -eu\n"
               + "[ \"$(id -u)\" = 0 ]\n"
               + "[ \"$(getprop ro.product.model)\" = OPD2515 ] || { echo 'Only OPD2515 supported';"
               + " exit 1; }\n"
               + "[ \"$(getprop ro.build.version.sdk)\" = 36 ] || { echo 'Only Android 16"
               + " supported'; exit 1; }\n"
               + "S="
        + STOCK
        + "\nD="
        + DEST
        + "\nR="
        + STATE
        + "\n[ ! -L \"$D\" ] && [ ! -L \"$R\" ]\n";
  }

  void load() throws Exception {
    String s =
        run(
            guards()
                + "[ -f \"$S\" ]\n"
                + "base64 \"$S\" | tr -d '\\n"
                + "'\n"
                + "echo\n"
                + "if [ -f \"$D\" ]; then base64 \"$D\" | tr -d '\\n"
                + "'; else printf ABSENT; fi\n"
                + "echo\n");
    String[] lines = s.split("\\n");
    if (lines.length != 2) throw new IOException("Unexpected root response");
    stock = new String(Base64.decode(lines[0], Base64.DEFAULT), StandardCharsets.UTF_8);
    current =
        lines[1].equals("ABSENT")
            ? null
            : new String(Base64.decode(lines[1], Base64.DEFAULT), StandardCharsets.UTF_8);
    stockHash = hash(stock);
    currentHash = current == null ? "ABSENT" : hash(current);
    new Policy(stock);
    if (current != null) new Policy(current);
  }

  String check() {
    return "[ \"$(sha256sum \"$S\" | cut -d' ' -f1)\" = '"
        + stockHash
        + "' ] || { echo 'Stock policy changed; reload'; exit 1; }\n"
        + "actual=ABSENT\n"
        + "if [ -f \"$D\" ]; then actual=$(sha256sum \"$D\" | cut -d' ' -f1); fi\n"
        + "[ \"$actual\" = '"
        + currentHash
        + "' ] || { echo 'Override changed; reload'; exit 1; }\n"
        + "if [ -d \"$R\" ]; then\n"
        + " [ -f \"$R/ready\" ] || { echo 'Incomplete transaction; backup requires recovery'; exit"
        + " 1; }\n"
        + " [ \"$(cat \"$R/expected\")\" = \"$actual\" ] || { echo 'Another tool changed the"
        + " override; refusing overwrite'; exit 1; }\n"
        + " [ \"$(cat \"$R/stock-hash\")\" = '"
        + stockHash
        + "' ] || { echo 'Firmware policy changed; manual review required'; exit 1; }\nfi\n";
  }

  void restoreOriginal() throws Exception {
    String encoded =
        run(
            guards()
                + "[ -f \"$R/original.xml\" ] || { echo 'No pre-manager override backup; use"
                + " Restore Stock'; exit 1; }\n"
                + "base64 \"$R/original.xml\" | tr -d '\\n"
                + "'\n");
    String xml = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
    new Policy(xml);
    write(xml, false);
  }

  void write(String xml, boolean restore) throws Exception {
    String encoded =
        xml == null
            ? ""
            : Base64.encodeToString(xml.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    String script =
        guards()
            + "L=$D.manager-lock\n"
            + "mkdir \"$L\" || { echo 'Another operation or interrupted lock exists'; exit 1; }\n"
            + "trap 'rm -rf \"$L\"' EXIT\n"
            + check()
            + "umask 077\n"
            + "if [ ! -d \"$R\" ]; then\n"
            + " mkdir \"$R\"\n"
            + " chown 0:0 \"$R\"\n"
            + " chmod 700 \"$R\"\n"
            + " if [ -f \"$D\" ]; then cp -p \"$D\" \"$R/original.xml\"; else touch"
            + " \"$R/original-absent\"; fi\n"
            + " printf '%s' '"
            + stockHash
            + "' > \"$R/stock-hash\"\n"
            + " printf '%s' \"$actual\" > \"$R/expected\"\n"
            + " touch \"$R/ready\"\n"
            + "fi\n"
            + (restore
                ? "[ -f \"$R/original-absent\" ] || { echo 'Existing override was present before"
                      + " manager; Restore Stock refused to preserve it'; exit 1; }\n"
                : "base64 -d > \"$L/new.xml\" <<'POLICY_BASE64'\n"
                    + encoded
                    + "\nPOLICY_BASE64\n[ \"$(sha256sum \"$L/new.xml\" | cut -d' ' -f1)\" = '"
                    + hash(xml)
                    + "' ]\n"
                    + "chown 1000:1000 \"$L/new.xml\"\n"
                    + "chmod 0644 \"$L/new.xml\"\n"
                    + "chcon u:object_r:oplus_multimedia_file:s0 \"$L/new.xml\"\n"
                    + "[ \"$(stat -c '%u:%g:%a' \"$L/new.xml\")\" = 1000:1000:644 ]\n"
                    + "ls -Z \"$L/new.xml\" | grep -q 'u:object_r:oplus_multimedia_file:s0'\n")
            + "if [ -f \"$D\" ]; then cp -p \"$D\" \"$L/previous.xml\"; chcon"
            + " u:object_r:oplus_multimedia_file:s0 \"$L/previous.xml\"; fi\n"
            + "cp \"$R/expected\" \"$L/previous-hash\"\n"
            + "rm \"$R/ready\"\n"
            + "committed=0\n"
            + "rollback() {\n"
            + " if [ \"$committed\" = 0 ]; then\n"
            + "  if [ -f \"$L/previous.xml\" ]; then mv -f \"$L/previous.xml\" \"$D\"; else rm -f"
            + " \"$D\"; fi\n"
            + "  cp \"$L/previous-hash\" \"$R/expected\" && touch \"$R/ready\"\n"
            + "  kill $(pidof audioserver) 2>/dev/null || true\n"
            + " fi\n"
            + " rm -rf \"$L\"\n"
            + "}\n"
            + "trap rollback EXIT\n"
            + (restore
                ? "rm -f \"$D\"\nprintf ABSENT > \"$R/expected\"\n"
                : "mv -f \"$L/new.xml\" \"$D\"\nprintf '%s' '"
                    + hash(xml)
                    + "' > \"$R/expected\"\n")
            + "oldpid=$(pidof audioserver || true)\n"
            + "count=0\n"
            + "while [ -z \"$oldpid\" ]; do\n"
            + " sleep 1\n"
            + " oldpid=$(pidof audioserver || true)\n"
            + " count=$((count + 1))\n"
            + " [ \"$count\" -lt 15 ] || { echo 'Audio service unavailable; rolling back'; exit 1;"
            + " }\n"
            + "done\n"
            + "kill $oldpid || { echo 'Could not restart audioserver'; exit 1; }\n"
            + "count=0\n"
            + "while :; do\n"
            + " sleep 1\n"
            + " newpid=$(pidof audioserver || true)\n"
            + " if [ -n \"$newpid\" ] && [ \"$newpid\" != \"$oldpid\" ]; then break; fi\n"
            + " count=$((count + 1))\n"
            + " [ \"$count\" -lt 15 ] || { echo 'Audio restart timed out; rolling back'; exit 1;"
            + " }\n"
            + "done\n"
            + "touch \"$R/ready\"\n"
            + "committed=1\n"
            + "echo 'Saved. Audio service restarted. MMAP eligibility requires reboot.'\n";
    run(script);
    load();
  }
}
