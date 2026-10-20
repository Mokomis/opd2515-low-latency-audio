// SPDX-License-Identifier: GPL-3.0-only
package dev.opd2515.audiomanager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Full-document editor: only the requested entries and version are changed. */
public final class Policy {
  static final String[] LISTS = {"aaudio-compatible-apps", "ull-compatible-apps"};
  final Document doc;

  public Policy(String xml) throws Exception {
    if (xml.length() > 8 * 1024 * 1024 || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
      throw new IOException("Unsafe policy XML");
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setExpandEntityReferences(false);
    doc =
        f.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    for (String list : LISTS) validate(one(list));
    Long.parseLong(one("version").getTextContent().trim());
  }

  Element one(String name) throws IOException {
    NodeList n = doc.getElementsByTagName(name);
    if (n.getLength() != 1) throw new IOException("Expected exactly one " + name);
    return (Element) n.item(0);
  }

  void validate(Element list) throws IOException {
    boolean needAttribute = false;
    Set<String> seen = new HashSet<>();
    for (Node n = list.getFirstChild(); n != null; n = n.getNextSibling())
      if (n instanceof Element) {
        if (!needAttribute && n.getNodeName().equals("name")) {
          if (!seen.add(n.getTextContent().trim())) throw new IOException("Duplicate policy entry");
          needAttribute = true;
        } else if (needAttribute && n.getNodeName().equals("attribute")) needAttribute = false;
        else throw new IOException("Unsupported list structure");
      }
    if (needAttribute) throw new IOException("Missing attribute");
  }

  public int eligibility(String pkg) throws Exception {
    int count = 0;
    for (String list : LISTS)
      for (Node n = one(list).getFirstChild(); n != null; n = n.getNextSibling())
        if (n instanceof Element
            && n.getNodeName().equals("name")
            && n.getTextContent().trim().equals(pkg)) {
          Node a = n.getNextSibling();
          while (a != null && !(a instanceof Element)) a = a.getNextSibling();
          if (a != null && a.getTextContent().trim().equals("0")) count++;
        }
    return count;
  }

  public String change(String pkg, boolean on, long stockVersion) throws Exception {
    if (!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) throw new IOException("Invalid package");
    for (String list : LISTS) {
      Element parent = one(list);
      for (Node n = parent.getFirstChild(); n != null; ) {
        Node next = n.getNextSibling();
        if (n instanceof Element
            && n.getNodeName().equals("name")
            && n.getTextContent().trim().equals(pkg)) {
          Node a = next;
          while (a != null && !(a instanceof Element)) a = a.getNextSibling();
          next = a.getNextSibling();
          parent.removeChild(a);
          parent.removeChild(n);
        }
        n = next;
      }
      if (on) {
        Element name = doc.createElement("name");
        name.setTextContent(pkg);
        parent.appendChild(name);
        Element attr = doc.createElement("attribute");
        attr.setTextContent("0");
        parent.appendChild(attr);
      }
    }
    long v = Math.max(version(), stockVersion);
    if (v == Long.MAX_VALUE) throw new IOException("Version overflow");
    one("version").setTextContent(Long.toString(v + 1));
    StringWriter out = new StringWriter();
    TransformerFactory.newInstance()
        .newTransformer()
        .transform(new DOMSource(doc), new StreamResult(out));
    String result = out.toString();
    new Policy(result);
    return result;
  }

  public long version() throws Exception {
    return Long.parseLong(one("version").getTextContent().trim());
  }
}
