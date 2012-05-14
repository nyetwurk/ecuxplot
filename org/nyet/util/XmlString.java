package org.nyet.util;

import java.lang.Appendable;
import java.lang.CharSequence;
import java.lang.String;
import java.lang.StringBuffer;

import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;

public class XmlString implements CharSequence, Appendable {
    // Members
    private StringBuffer buf=new StringBuffer();
    private int ShiftWidth=2;
    private int Indent=0;
    private static final String EOL="\n";

    // Constructors
    public XmlString() { }
    public XmlString(int i) { this.indent(i); }
    public XmlString(String s) { this.append(s); }
    public XmlString(int i, String s) { this.indent(i); this.append(s); }

    // CharSequence methods
    public String toString() { return this.buf.toString(); }
    public char charAt(int index) { return this.buf.charAt(index); }
    public int length() { return this.buf.length(); }
    public CharSequence subSequence(int start, int end) {
	return this.buf.subSequence(start, end);
    }

    // Static methods
    private static String escape(String s)
    {
	//return StringEscapeUtils.escapeXml(s);
	return StringEscapeUtils.escapeHtml3(s);
	//return StringEscapeUtils.escapeHtml4(s);
    }

    private static String tagIt(String tag, int value)
    {
	tag = escape(tag);
	return String.format("%s>%d</%s", tag, value, tag);
    }
    private static String tagIt(String tag, Object value)
    {
	tag = escape(tag);
	String v = escape(value.toString());
	return String.format("%s>%s</%s", tag, v, tag);
    }

    // Methods
    public Appendable append(char c) { return this.buf.append(c); }
    public Appendable append(CharSequence cs, int start, int end) { return this.append(cs.subSequence(start,end)); }
    public Appendable append(CharSequence cs) { return this.append(cs.toString()); }
    public Appendable append(String s) {
	this.doIndent();
	return this.buf.append("<"+s+">" + EOL);
    }

    public Appendable append(String tag, Object value) {
	if (tag.length()<=0) return this.buf;
	if (value==null) return this.append(escape(tag));
	if (value.toString().length()<=0) return this.buf;
	return this.append(tagIt(tag, value));
    }

    public Appendable append(String tag, Map<String, Object> attrs) { return this.append(tag, attrs, true); }
    public Appendable append(String tag, Map<String, Object> attrs, boolean leaf) {
	this.doIndent();
	this.buf.append(String.format("<%s", escape(tag)));
	for (Map.Entry<String, Object> e: attrs.entrySet()) {
	    String k = escape(e.getKey());
	    String v = escape(e.getValue().toString());
	    this.buf.append(String.format(" %s=\"%s\"", k, v));
	}
	return this.buf.append((leaf?" />":">") + EOL);
    }

    private Appendable doIndent() {
	if (this.Indent>0)
	    this.buf.append(String.format("%" + (this.ShiftWidth*Indent) + "s", " "));
	return this.buf;
    }

    public int shiftWidth() { return this.ShiftWidth; }
    public void shiftWidth(int sw) { this.ShiftWidth = sw; }

    public int indent() { return this.Indent++; }
    public int indent(int i) { int ret = this.Indent; this.Indent+=i; return ret; }
    public int unindent() { return this.Indent--; }
}
