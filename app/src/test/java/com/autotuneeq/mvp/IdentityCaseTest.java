package com.autotuneeq.mvp;

import org.junit.Test;
import static org.junit.Assert.*;

/** The frozen tables keep identity keys independent of the JDK / Android / Python Unicode version. */
public final class IdentityCaseTest {
    @Test public void lowerCasingUsesTheFrozenUnicode10TableOnly() {
        assertEquals("abc xyz",IdentityCase.lower("ABC XYZ"));
        assertEquals("\u00e9\u00df",IdentityCase.lower("\u00c9\u1e9e"));            // E-acute, capital sharp s -> sharp s
        assertEquals("i",IdentityCase.lower("\u0130"));                            // simple mapping, not the two-code-point full mapping
        assertEquals("\u03c3\u03bf\u03c6\u03bf\u03c3",IdentityCase.lower("\u03a3\u039f\u03a6\u039f\u03a3"));
        assertEquals("\u03c3",IdentityCase.lower("\u03c2"));                       // final sigma folded to sigma
        assertEquals("\u01c6",IdentityCase.lower("\u01c5"));                       // title-case digraph
        assertEquals("\ud801\udc28",IdentityCase.lower("\ud801\udc00"));           // Deseret (supplementary plane, Unicode 3.1)
    }
    @Test public void lettersAddedAfterUnicode10AreNeverCaseMappedOnAnyRuntime() {
        // U+2C2F, U+A7C0 and Vithkuqi U+10570 got lowercase forms in Unicode 14: JDK 17 lacks them, Python 3.12 has them.
        assertEquals("\u2c2f",IdentityCase.lower("\u2c2f"));
        assertEquals("\ua7c0",IdentityCase.lower("\ua7c0"));
        assertEquals("\ud801\udd70",IdentityCase.lower("\ud801\udd70"));
        assertEquals("\u1c90",IdentityCase.lower("\u1c90"));                       // Georgian Mtavruli, Unicode 11
    }
    @Test public void nfcIsOnlyAppliedToTextAssignedInUnicode9() {
        assertTrue(IdentityCase.nfcSafe("Cafe\u0301 \u00e9 \u4e2d\u6587 \ud83c\udfb5"));
        assertTrue(IdentityCase.nfcSafe(""));
        assertFalse(IdentityCase.nfcSafe("e\u0898"));                              // Arabic mark added in Unicode 14
        assertFalse(IdentityCase.nfcSafe("x\u2c2f"));
        assertFalse(IdentityCase.nfcSafe("a\ud800"));                              // lone surrogate
        assertEquals("caf\u00e9",TrackIdentity.canonicalTitle("Cafe\u0301","x"));            // NFC applied
        assertEquals("cafe\u0301e\u0898",TrackIdentity.canonicalTitle("Cafe\u0301e\u0898","x"));         // not composed: text contains a post-Unicode-9 mark
        assertEquals("e\u0898\u0323",TrackIdentity.canonicalTitle("e\u0898\u0323","x"));       // no canonical reordering for unsafe text
    }
    @Test public void keysAreStableAcrossTheCaseVariantsOfOldAndNewLetters() {
        assertEquals(TrackIdentity.key("\u00c9COLE","A"),TrackIdentity.key("\u00e9cole","A"));
        assertNotEquals(TrackIdentity.key("\u2c2f","A"),TrackIdentity.key("\u2c5f","A"));  // no mapping => distinct and deterministic
    }
}
