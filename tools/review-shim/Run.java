import java.lang.reflect.*; import org.junit.Test;
public class Run { public static void main(String[] a) throws Exception { int ok=0,bad=0;
  for(String cn:a){ Class<?> c=Class.forName(cn);
    for(Method m:c.getDeclaredMethods()){ Test t=m.getAnnotation(Test.class); if(t==null) continue;
      Object o=c.getDeclaredConstructor().newInstance();
      try{ m.invoke(o); if(t.expected()!=Test.None.class){ bad++; System.out.println("FAIL "+cn+"."+m.getName()+": expected "+t.expected().getSimpleName()); } else { ok++; System.out.println("ok   "+cn.substring(cn.lastIndexOf('.')+1)+"."+m.getName()); } }
      catch(InvocationTargetException e){ Throwable x=e.getCause(); if(t.expected()!=Test.None.class && t.expected().isInstance(x)){ ok++; System.out.println("ok   "+cn.substring(cn.lastIndexOf('.')+1)+"."+m.getName()+" (expected "+x.getClass().getSimpleName()+")"); } else { bad++; System.out.println("FAIL "+cn+"."+m.getName()+": "+x); } } } }
  System.out.println("RESULT passed="+ok+" failed="+bad); System.exit(bad==0?0:1); } }
