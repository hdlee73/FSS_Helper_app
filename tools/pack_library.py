#!/usr/bin/env python3
"""
검사품질관리 매뉴얼·검사관련 서식을 앱에 넣을 암호화 묶음(secure/assets/library.bin)으로 만든다.

  python3 tools/pack_library.py --manual-pdf manual.pdf --forms-pdf forms.pdf \
      --forms-hwp forms.hwp --key <LIBRARY_KEY(base64 32바이트)>

- 원문(PDF·HWP)은 저장소에 넣지 않는다. 이 스크립트가 만든 library.bin(AES-256-GCM 암호문)만 커밋한다.
- 열쇠(LIBRARY_KEY)는 GitHub Secrets에만 둔다. 새 열쇠는 --new-key 로 만든다.
- 필요: poppler-utils(pdftotext), pip install cryptography
- 매뉴얼·서식 PDF는 한글(한컴오피스)에서 'PDF로 저장'한 파일을 쓰면 원본과 가장 똑같다.
"""
import argparse, base64, io, json, os, re, subprocess, sys, zipfile

SECTION_NO = re.compile(r"^\s*(0[1-9]|1[0-9]|2[0-9])\s*$")
MARK_FLAT = re.compile(r"<제(\d+)-(\d+)호서식>")


def page_texts(pdf):
    out = subprocess.run(["pdftotext", "-enc", "UTF-8", pdf, "-"], check=True, capture_output=True).stdout.decode("utf-8", "replace")
    pages = out.split("\f")
    if pages and not pages[-1].strip():
        pages = pages[:-1]
    return pages


def squash(s):
    return "".join(ch for ch in s if ch.isalnum())


def manual_toc(pages):
    """'01 ↵ 절 제목' 꼴의 절 머리를 찾아 목차로 쓴다. 절 머리가 5개 이상 몰린 쪽(차례)은 건너뛴다."""
    found = []
    for p, text in enumerate(pages):
        lines = [l.strip() for l in text.splitlines()]
        heads = []
        for i, l in enumerate(lines):
            if SECTION_NO.match(l):
                j = i + 1
                while j < len(lines) and not lines[j]:
                    j += 1
                if j < len(lines):
                    t = lines[j]
                    if 3 <= len(t) <= 40 and not t.startswith(("□", "◦", "*", "-")):
                        heads.append(t)
        if len(heads) >= 5:
            continue
        for t in heads:
            found.append((t, p))
    seen, toc = set(), []
    for t, p in found:
        k = squash(t)
        if k in seen:
            continue
        seen.add(k)
        toc.append({"title": t, "page": p})
    return toc


def form_marks(pages):
    marks = []
    for t in pages:
        for m in MARK_FLAT.finditer("".join(t.split())):
            marks.append({"no": f"{m.group(1)}-{m.group(2)}", "text": f"< 제{m.group(1)}-{m.group(2)}호 서식 >"})
    return marks


def form_spans(pages):
    hits = []
    for p, t in enumerate(pages):
        flat = "".join(t.split())
        for m in MARK_FLAT.finditer(flat):
            hits.append((f"{m.group(1)}-{m.group(2)}", p, m.start() < 40))
    out = {}
    for i, (no, page, _top) in enumerate(hits):
        nxt = next((h for h in hits[i + 1:] if h[0] != no), None)
        if nxt is None:
            end = len(pages) - 1
        elif nxt[1] == page:
            end = page
        elif nxt[2]:
            end = nxt[1] - 1
        else:
            end = nxt[1]
        end = max(end, page)
        if no in out:
            out[no] = {"start": min(out[no]["start"], page), "end": max(out[no]["end"], end)}
        else:
            out[no] = {"start": page, "end": end}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manual-pdf")
    ap.add_argument("--forms-pdf")
    ap.add_argument("--forms-hwp")
    ap.add_argument("--manual-name", default="검사품질관리 매뉴얼")
    ap.add_argument("--forms-name", default="검사관련 서식")
    ap.add_argument("--key", default=os.environ.get("LIBRARY_KEY", ""))
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "secure", "assets", "library.bin"))
    ap.add_argument("--new-key", action="store_true", help="새 열쇠를 만들어 출력하고 끝낸다")
    a = ap.parse_args()

    if a.new_key:
        print(base64.b64encode(os.urandom(32)).decode())
        return
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    key = base64.b64decode(a.key)
    if len(key) != 32:
        sys.exit("LIBRARY_KEY는 base64로 쓴 32바이트여야 합니다(--new-key로 만들기).")

    data = {"manualName": "", "manualSrc": "", "manualPdf": "", "manualTitles": [], "manualToc": [],
            "formsName": "", "formsSrc": "", "formsPdf": "", "formMarks": [], "formPages": {}, "importedAt": 0}
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        if a.manual_pdf:
            pages = page_texts(a.manual_pdf)
            toc = manual_toc(pages)
            z.write(a.manual_pdf, "manual.pdf")
            z.writestr("manual.pages.txt", "\f".join(pages))
            data.update(manualName=a.manual_name, manualPdf="manual.pdf", manualToc=toc, manualTitles=[t["title"] for t in toc])
            print(f"매뉴얼: {len(pages)}쪽, 목차 {len(toc)}개")
        if a.forms_pdf:
            pages = page_texts(a.forms_pdf)
            spans = form_spans(pages)
            z.write(a.forms_pdf, "forms.pdf")
            z.writestr("forms.pages.txt", "\f".join(pages))
            data.update(formsName=a.forms_name, formsPdf="forms.pdf", formMarks=form_marks(pages), formPages=spans)
            print(f"서식: {len(pages)}쪽, 서식 {len(spans)}종")
        if a.forms_hwp:
            z.write(a.forms_hwp, "forms.hwp")
            data.update(formsSrc="forms.hwp")
        z.writestr("library.json", json.dumps(data, ensure_ascii=False))
    nonce = os.urandom(12)
    blob = b"FSSL1" + nonce + AESGCM(key).encrypt(nonce, buf.getvalue(), None)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "wb") as f:
        f.write(blob)
    print(f"만듦: {os.path.abspath(a.out)} ({len(blob):,} 바이트, 암호화됨)")


if __name__ == "__main__":
    main()
