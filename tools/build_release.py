"""Build the committed release with the original key and write a provenance receipt.

Requires Git, JDK 17, Android SDK (ANDROID_HOME), and the repository-external
MY_CHUANNONG_SIGNING_CONFIG. Never creates keys or reads/prints their passwords.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def run(*args, capture=False):
    result = subprocess.run(args, cwd=ROOT, check=True, text=True,
                            encoding="utf-8", errors="replace",
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else None


def clean_commit():
    if run("git", "status", "--porcelain", "--untracked-files=all", capture=True):
        raise RuntimeError("Commit or preserve pending source changes before delivery.")
    return run("git", "rev-parse", "HEAD", capture=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--review-without-services", action="store_true",
                        help="Explicit local review only; backend/map may be unavailable.")
    args = parser.parse_args()
    output = args.output_dir.resolve()
    # Delivery artifacts must stay outside source control, even when ignored.
    if output == ROOT or ROOT in output.parents:
        raise RuntimeError("Choose an output directory outside the source repository.")
    apk_name = "my川农_chuannong_1.0.0.apk"
    if (output / apk_name).exists() or (output / "delivery-manifest.json").exists():
        raise RuntimeError("Use an empty delivery destination; existing packages are preserved.")
    if not os.environ.get("MY_CHUANNONG_SIGNING_CONFIG"):
        raise RuntimeError("Set MY_CHUANNONG_SIGNING_CONFIG to the original signing configuration.")
    sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_value:
        raise RuntimeError("Set ANDROID_HOME to the local Android SDK.")
    sdk = Path(sdk_value)
    build_tools = sdk / "build-tools" / "36.0.0"
    signer = build_tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
    aapt = build_tools / ("aapt.exe" if os.name == "nt" else "aapt")
    if not signer.is_file() or not aapt.is_file():
        raise RuntimeError("Install Android SDK build-tools 36.0.0.")
    commit = clean_commit()
    run(sys.executable, "tools/verify_checkout.py")
    wrapper = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    flags = ["--no-configuration-cache", "--console=plain"]
    if args.review_without_services:
        flags.append("-PreleaseReviewWithoutServices=true")
    run(wrapper, ":app:validateReleaseConfiguration", *flags)
    run(wrapper, ":app:assembleRelease", *flags)
    apk = ROOT / "app/build/outputs/apk/release" / apk_name
    if not apk.is_file():
        raise RuntimeError("Expected release APK was not produced.")
    certificate_output = run(str(signer), "verify", "--verbose", "--print-certs", str(apk), capture=True)
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", certificate_output)
    source = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    expected = re.search(r'val officialSigningCertificateSha256 = "([0-9A-F]{64})"', source)
    if not expected or len(certificates) != 1 or certificates[0].upper() != expected.group(1):
        raise RuntimeError("APK does not carry the original release certificate.")
    badging = run(str(aapt), "dump", "badging", str(apk), capture=True)
    if (not re.search(r"package: name='com\.mychuannong\.app' versionCode='10' versionName='1\.0\.0'", badging)
            or "application-debuggable" in badging):
        raise RuntimeError("APK is not the expected non-debuggable 1.0.0 / 10 release.")
    if clean_commit() != commit:
        raise RuntimeError("Source changed during the build; no package delivered.")
    run(sys.executable, "tools/verify_checkout.py")
    build_config = ROOT / "app/build/generated/source/buildConfig/release/com/mychuannong/app/BuildConfig.java"
    receipt = {
        "version": "1.0.0", "versionCode": 10, "variant": "release",
        "packageName": "com.mychuannong.app", "androidCommit": commit,
        "sourceManifestSha256": hashlib.sha256((ROOT / "docs/delivery/accepted-source-manifest.json").read_bytes()).hexdigest(),
        "buildConfigSha256": hashlib.sha256(build_config.read_bytes()).hexdigest(),
        "reviewWithoutServices": args.review_without_services,
        "signingCertificateSha256": certificates[0].lower(),
        "apkFile": apk_name, "apkSize": apk.stat().st_size,
        "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "buildToolsVersion": "36.0.0",
    }
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, output / apk_name)
    (output / "delivery-manifest.json").write_text(
        json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Release verified and delivered: {output / apk_name}")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
