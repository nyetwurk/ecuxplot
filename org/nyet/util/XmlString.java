package org.nyet.util;

import java.lang.CharSequence;
import java.lang.String;
import java.lang.StringBuffer;

import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;

public class XmlString implements CharSequence {
    // Members
    private StringBuffer buf;
    private int ShiftWidth=2;
    private static final String EOL="\n";

    // Constructors
    public XmlString(String s) { this.buf=new StringBuffer(s); }
    public XmlString(int indent, String s) {
	this.buf=new StringBuffer("<"+s+">");
	if (indent>0)
	    this.indent(indent);
    }
    public XmlString(String tag, Object value) { this(0, tag, value); }
    public XmlString(int indent, String tag, Object value) {
	this(tagIt(tag, value));
	if (indent>0)
	    this.indent(indent);
    }
    public XmlString(String tag, Map<String, Object> attrs) { this(0,tag, attrs); }
    public XmlString(int indent, String tag, Map<String, Object> attrs) { this(indent, tag, attrs, true); }
    public XmlString(int indent, String tag, Map<String, Object> attrs, boolean leaf) {
	this(String.format("<%s", StringEscapeUtils.escapeXml(tag)));
	if (indent>0)
	    this.indent(indent);
	for (Map.Entry<String, Object> e: attrs.entrySet()) {
	    String k = StringEscapeUtils.escapeXml(e.getKey());
	    String v = StringEscapeUtils.escapeXml(e.getValue().toString());
	    this.buf.append(String.format(" %s=\"%s\"", k, v));
	}
	this.buf.append(leaf?" />":">");
    }

    // CharSequence methods
    public String toString() { return this.buf.toString(); }
    public char charAt(int index) { return this.buf.charAt(index); }
    public int length() { return this.buf.length(); }
    public CharSequence subSequence(int start, int end) {
	return this.buf.subSequence(start, end);
    }

    // Static methods
    private static String tagIt(String tag, int value)
    {
	tag = StringEscapeUtils.escapeXml(tag);
	return String.format("<%s>%d</%s>", tag, value, tag);
    }
    private static String tagIt(String tag, Object value)
    {
	if(tag.length()<=0 || value.toString().length()<=0) return "";
	tag = StringEscapeUtils.escapeXml(tag);
	String v = StringEscapeUtils.escapeXml(value.toString());
	return String.format("<%s>%s</%s>", tag, v, tag);
    }

    public static String factory(int i, String s) {
	return new XmlString(i,s).toString()+EOL;
    }

    public static String factory(int i, String t, Object v) {
	if (t.length()<=0 || v.toString().length()<=0) return "";
	return new XmlString(i,t,v).toString()+EOL;
    }

    public static String factory(int i, String t, int v) {
	if (t.length()<=0) return "";
	return new XmlString(i,t,v).toString()+EOL;
    }

    public static String factory(int i, String t, Map<String, Object> a) {
	if (t.length()<=0) return "";
	return new XmlString(i,t,a).toString()+EOL;
    }
    public static String factory(int i, String t, Map<String, Object> a, boolean l) {
	if (t.length()<=0) return "";
	return new XmlString(i,t,a,l).toString()+EOL;
    }

    // Methods
    public int shiftWidth() { return this.ShiftWidth; }
    public void shiftWidth(int sw) { this.ShiftWidth = sw; }

    public StringBuffer indent(int n) {
	if (n>0) {
	    return this.buf.insert(0, String.format("%" + (this.ShiftWidth*n) + "s", " "));
	} else if (n<0) {
	    return this.buf.delete(0, this.ShiftWidth*n);
	}
	return this.buf;
    }

    public StringBuffer indent() {
	return this.indent(1);
    }
}
