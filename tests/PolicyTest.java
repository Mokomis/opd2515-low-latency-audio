package dev.opd2515.audiomanager;

public final class PolicyTest {
  static void check(boolean ok) {
    if (!ok) throw new AssertionError();
  }

  public static void main(String[] args) throws Exception {
    String xml =
        "<filter-conf><version>12</version><other>keep &amp;"
            + " preserve</other><aaudio-compatible-apps><name>existing.app</name><attribute>0</attribute></aaudio-compatible-apps><ull-compatible-apps><name>existing.app</name><attribute>0</attribute></ull-compatible-apps></filter-conf>";
    Policy p = new Policy(xml);
    String a = p.change("added.app", true, 15);
    p = new Policy(a);
    check(p.version() == 16);
    check(p.eligibility("added.app") == 2);
    check(p.eligibility("existing.app") == 2);
    p = new Policy(p.change("second.app", true, 15));
    p = new Policy(p.change("added.app", false, 15));
    check(p.eligibility("added.app") == 0);
    check(p.eligibility("second.app") == 2);
    check(p.eligibility("existing.app") == 2);
    check(p.doc.getElementsByTagName("other").item(0).getTextContent().equals("keep & preserve"));
    for (String bad :
        new String[] {
          xml.replace("</filter-conf>", "<version>9</version></filter-conf>"),
          xml.replace("<attribute>0</attribute>", ""),
          "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]>" + xml
        }) {
      try {
        new Policy(bad);
        throw new AssertionError("accepted bad XML");
      } catch (java.io.IOException | org.xml.sax.SAXException expected) {
      }
    }
    try {
      p.change("bad';id", true, 1);
      throw new AssertionError();
    } catch (java.io.IOException expected) {
    }
    System.out.println(
        "PASS: preservation, multiple selections, OFF isolation, versions, escaping,"
            + " malformed/duplicate XML, injection rejection");
  }
}
