package org.junit;
/** Minimal re-implementation of the JUnit4 Assert methods used by this project's tests (NOT the real JUnit). */
public class Assert {
  public static void fail(String m){ throw new AssertionError(m); }
  public static void assertTrue(boolean c){ if(!c) throw new AssertionError("expected true"); }
  public static void assertTrue(String m,boolean c){ if(!c) throw new AssertionError(m); }
  public static void assertFalse(boolean c){ if(c) throw new AssertionError("expected false"); }
  public static void assertFalse(String m,boolean c){ if(c) throw new AssertionError(m); }
  public static void assertNull(Object o){ if(o!=null) throw new AssertionError("expected null: "+o); }
  public static void assertNotNull(Object o){ if(o==null) throw new AssertionError("expected non-null"); }
  public static void assertEquals(long a,long b){ if(a!=b) throw new AssertionError("expected "+a+" got "+b); }
  public static void assertEquals(Object a,Object b){ if(!(a==null?b==null:a.equals(b))) throw new AssertionError("expected "+a+" got "+b); }
  public static void assertEquals(double a,double b,double d){ if(!(Math.abs(a-b)<=d)) throw new AssertionError("expected "+a+" got "+b+" (delta "+d+")"); }
  public static void assertNotEquals(String m,Object a,Object b){ if(a==null?b==null:a.equals(b)) throw new AssertionError(m+": values should differ: "+a); }
  public static void assertNotEquals(Object a,Object b){ if(a==null?b==null:a.equals(b)) throw new AssertionError("values should differ: "+a); }
  public static void assertArrayEquals(double[] a,double[] b,double d){ if(a.length!=b.length) throw new AssertionError("length"); for(int i=0;i<a.length;i++) assertEquals(a[i],b[i],d); }
  public static void assertArrayEquals(Object[] a,Object[] b){ if(!java.util.Arrays.equals(a,b)) throw new AssertionError("arrays differ"); }
}
