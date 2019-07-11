package org.nyet.util;

import java.lang.Appendable;
import java.lang.CharSequence;
import java.lang.String;
import java.lang.StringBuffer;

import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.text.translate.LookupTranslator;

public class XmlString implements CharSequence, Appendable {
    // Members
    private final StringBuffer buf=new StringBuffer();
    private int ShiftWidth=2;
    private int Indent=0;
    private static final String EOL="\n";
    private final LookupTranslator lt;

    // Constructors
    public XmlString() {
	final String[][] sb = new String[128][2];
	for(char i=0; i<128; i++) {
	    sb[i][0] = String.format("%c",i+128);
	    sb[i][1] = "&#" + (i+128) + ";";
	}
	this.lt = new LookupTranslator(sb);
    }
    public XmlString(int i) { this(); this.indent(i); }
    public XmlString(String s) { this(); this.append(s); }
    public XmlString(int i, String s) { this(); this.indent(i); this.append(s); }

    // CharSequence methods
    @Override
    public String toString() { return this.buf.toString(); }
    @Override
    public char charAt(int index) { return this.buf.charAt(index); }
    @Override
    public int length() { return this.buf.length(); }
    @Override
    public CharSequence subSequence(int start, int end) {
	return this.buf.subSequence(start, end);
    }

    // Methods
    private String escape(String s)
    {
	return this.lt.translate(StringEscapeUtils.escapeXml11(s.trim()))
	    .replaceAll("\r","&#013;").replaceAll("\n","&#010;");
	//return StringEscapeUtils.escapeHtml3(s);
	//return StringEscapeUtils.escapeHtml4(s);
    }
    /*
    private Appendable tagIt(String tag, int value)
    {
	tag = escape(tag);
	return this.append(String.format("%s>%d</%s", tag, value, tag));
    }
    */
    private Appendable tagIt(String tag, Object value)
    {
	tag = escape(tag);
	final String v = escape(value.toString());
	return this.append(String.format("%s>%s</%s", tag, v, tag));
    }

    @Override
    public Appendable append(char c) { return this.buf.append(c); }
    @Override
    public Appendable append(CharSequence cs, int start, int end) { return this.append(cs.subSequence(start,end)); }
    @Override
    public Appendable append(CharSequence cs) { return this.append(cs.toString()); }
    public Appendable append(String s) {
	this.doIndent();
	return this.buf.append("<"+s+">" + EOL);
    }

    public Appendable append(String tag, Object value) {
	if (tag.length()<=0) return this.buf;
	if (value==null) return this.append(escape(tag));
	if (value.toString().length()<=0) return this.buf;
	return this.tagIt(tag, value);
    }

    public Appendable append(String tag, Map<String, Object> attrs) {
	this.doIndent();
	this.buf.append(String.format("<%s", escape(tag)));
	for (final Map.Entry<String, Object> e: attrs.entrySet()) {
	    final String k = escape(e.getKey());
	    final String v = escape(e.getValue().toString());
	    this.buf.append(String.format(" %s=\"%s\"", k, v));
	}
	return this.buf.append(" />" + EOL);
    }

    private Appendable doIndent() {
	if (this.Indent>0)
	    this.buf.append(String.format("%" + (this.ShiftWidth*this.Indent) + "s", " "));
	return this.buf;
    }

    public int shiftWidth() { return this.ShiftWidth; }
    public void shiftWidth(int sw) { this.ShiftWidth = sw; }

    public int indent() { return this.Indent++; }
    public int indent(int i) { final int ret = this.Indent; this.Indent+=i; return ret; }
    public int unindent() { return this.Indent--; }
}
