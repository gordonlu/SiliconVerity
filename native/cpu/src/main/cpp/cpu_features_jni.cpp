#include <jni.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>
#include <string>
#include "sv_cpu_internal.h"

static std::string decode_arm_features() {
    unsigned long hwcap = getauxval(AT_HWCAP);
    unsigned long hwcap2 = getauxval(AT_HWCAP2);
    std::string s;
    auto add = [&](const char* name) {
        if (!s.empty()) s += ",";
        s += name;
    };

    if (hwcap & HWCAP_FP) add("fp");
    if (hwcap & HWCAP_ASIMD) add("asimd");
    if (hwcap & HWCAP_EVTSTRM) add("evtstrm");
    if (hwcap & HWCAP_AES) add("aes");
    if (hwcap & HWCAP_PMULL) add("pmull");
    if (hwcap & HWCAP_SHA1) add("sha1");
    if (hwcap & HWCAP_SHA2) add("sha2");
    if (hwcap & HWCAP_CRC32) add("crc32");
    if (hwcap & HWCAP_ATOMICS) add("atomics");
    if (hwcap & HWCAP_FPHP) add("fphp");
    if (hwcap & HWCAP_ASIMDHP) add("asimdhp");
    if (hwcap & HWCAP_CPUID) add("cpuid");
    if (hwcap & HWCAP_ASIMDRDM) add("asimdrdm");
    if (hwcap & HWCAP_JSCVT) add("jscvt");
    if (hwcap & HWCAP_FCMA) add("fcma");
    if (hwcap & HWCAP_LRCPC) add("lrcpc");
    if (hwcap & HWCAP_DCPOP) add("dcpop");
    if (hwcap & HWCAP_SHA3) add("sha3");
    if (hwcap & HWCAP_SM3) add("sm3");
    if (hwcap & HWCAP_SM4) add("sm4");
    if (hwcap & HWCAP_ASIMDDP) add("asimddp");
    if (hwcap & HWCAP_SHA512) add("sha512");
    if (hwcap & HWCAP_SVE) add("sve");
    if (hwcap & HWCAP_ASIMDFHM) add("asimdfhm");
    if (hwcap & HWCAP_DIT) add("dit");
    if (hwcap & HWCAP_USCAT) add("uscat");
    if (hwcap & HWCAP_ILRCPC) add("ilrcpc");
    if (hwcap & HWCAP_FLAGM) add("flagm");
    if (hwcap & HWCAP_SSBS) add("ssbs");
    if (hwcap & HWCAP_SB) add("sb");
    if (hwcap & HWCAP_PACA) add("paca");
    if (hwcap & HWCAP_PACG) add("pacg");

#ifdef HWCAP2_DCPODP
    if (hwcap2 & HWCAP2_DCPODP) add("dcpodp");
#endif
#ifdef HWCAP2_SVE2
    if (hwcap2 & HWCAP2_SVE2) add("sve2");
#endif
#ifdef HWCAP2_SVEAES
    if (hwcap2 & HWCAP2_SVEAES) add("sveaes");
#endif
#ifdef HWCAP2_SVEPMULL
    if (hwcap2 & HWCAP2_SVEPMULL) add("svepmull");
#endif
#ifdef HWCAP2_SVESHA3
    if (hwcap2 & HWCAP2_SVESHA3) add("svesha3");
#endif
#ifdef HWCAP2_SVESM4
    if (hwcap2 & HWCAP2_SVESM4) add("svesm4");
#endif
#ifdef HWCAP2_FLAGM2
    if (hwcap2 & HWCAP2_FLAGM2) add("flagm2");
#endif
#ifdef HWCAP2_FRINT
    if (hwcap2 & HWCAP2_FRINT) add("frint");
#endif
#ifdef HWCAP2_SVEI8MM
    if (hwcap2 & HWCAP2_SVEI8MM) add("svei8mm");
#endif
#ifdef HWCAP2_SVEF32MM
    if (hwcap2 & HWCAP2_SVEF32MM) add("svef32mm");
#endif
#ifdef HWCAP2_SVEF64MM
    if (hwcap2 & HWCAP2_SVEF64MM) add("svef64mm");
#endif
#ifdef HWCAP2_SVEBF16
    if (hwcap2 & HWCAP2_SVEBF16) add("svebf16");
#endif
#ifdef HWCAP2_I8MM
    if (hwcap2 & HWCAP2_I8MM) add("i8mm");
#endif
#ifdef HWCAP2_BF16
    if (hwcap2 & HWCAP2_BF16) add("bf16");
#endif
#ifdef HWCAP2_EBF16
    if (hwcap2 & HWCAP2_EBF16) add("ebf16");
#endif
#ifdef HWCAP2_DGH
    if (hwcap2 & HWCAP2_DGH) add("dgh");
#endif
#ifdef HWCAP2_RNG
    if (hwcap2 & HWCAP2_RNG) add("rng");
#endif
#ifdef HWCAP2_BTI
    if (hwcap2 & HWCAP2_BTI) add("bti");
#endif
#ifdef HWCAP2_MTE
    if (hwcap2 & HWCAP2_MTE) add("mte");
#endif

    return s;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_siliconverity_nativecpu_CpuFeatures_nativeFeatures(JNIEnv* env, jclass) {
    return env->NewStringUTF(decode_arm_features().c_str());
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_siliconverity_nativecpu_CpuFeatures_nativeHwcap(JNIEnv* env, jclass) {
    unsigned long hwcap = getauxval(AT_HWCAP);
    unsigned long hwcap2 = getauxval(AT_HWCAP2);
    jlong out[2] = { (jlong)hwcap, (jlong)hwcap2 };
    jlongArray result = env->NewLongArray(2);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 2, out);
    }
    return result;
}
