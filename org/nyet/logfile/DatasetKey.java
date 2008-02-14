    public class Key implements Comparable {
	private String s;
	private Integer series;
	public Key (String s, int series) {
	    this.s=s;
	    this.series=new Integer(series);
	}
	public String toString() { return this.s + " " + series; }
	public String getString() { return this.s; }
	public Integer getSeries() { return this.series; }

	public int compareTo(Object o) {
	    if(o instanceof Key) {
		Key k = (Key)o;
		int out = this.s.compareTo(k.s);
		if(out!=0) return out;
		return this.series.compareTo(k.series);
	    }
	    if(o instanceof String) {
		return this.s.compareTo((String)o);
	    }
	    throw new ClassCastException("Not a Key or a Comparable!");
	}
    }

