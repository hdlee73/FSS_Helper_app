# FSS_Helper (Android)

금융감독원 검사역이 검사업무에 늘 찾아보는 자료와 기준을 한곳에 모은 안드로이드 앱입니다. 윈도우판 [FSS_Helper](https://github.com/hdlee73/FSS_Helper)와 기능이 같고, 휴대폰(갤럭시 S23 기준)에 맞게 한 열 화면으로 바꿨습니다.

| 메뉴 | 하는 일 |
| --- | --- |
| 홈 | 법령 바로 검색, 도구 카드, 최근 사용 목록(서식·매뉴얼 쪽·법령·기업, 즐겨찾기) |
| 검사품질관리 매뉴얼 | 앱에 들어 있는 매뉴얼을 원본 모양(PDF)으로 보기, 목차·찾기, 두 손가락 확대 |
| 검사관련 서식 | 서식별 원본 보기, 한글 앱(한컴오피스)으로 열어 작성 |
| 법률 검색 | FSS_insights 방식: 항·호·목 바로 아래 위임 법규, 인용 법규는 따로. 1~5단 비교, 기준일 조회 |
| 기업 개황 | 기업개황·최대주주·재무·공시, 한글(HWPX)·PDF로 저장·공유 |
| 양정기준 / 과징금 / 과태료 | 산정 시뮬레이션과 제재규정·시행세칙 원문(탭으로 전환) |
| 할 일 | To Do 목록(기한·중요 표시) |
| 앱 정보 | 버전, 만든이, 업데이트, 기본 사용 방법 |

디자인: 시안 3(네이비 공문), 아이콘 A(문서+돋보기). 모든 메뉴는 앱 이름 아래 위쪽 탭(옆으로 밀어 넘김)에 있습니다. 뒤로가기는 앱을 끄지 않고 직전 화면으로 돌아가며, 홈에서 두 번 누르면 닫힙니다.

## 비밀 값(GitHub Secrets)

저장소에는 어떤 키도 들어가지 않습니다. **Settings → Secrets and variables → Actions**에 아래를 넣으면 빌드할 때 앱에 들어갑니다.

| 이름 | 내용 |
| --- | --- |
| `LAW_OC` | 국가법령정보 Open API OC |
| `DART_API_KEY` | OpenDART 인증키 |
| `LIBRARY_KEY` | 매뉴얼·서식 묶음 복호화 열쇠(아래) |
| `RELEASE_KEYSTORE_BASE64` | 배포 서명 키스토어(.jks)를 base64로 |
| `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` | 키스토어 비밀번호·별칭·키 비밀번호 |

배포 서명 키 만들기(한 번만, 잘 보관):

```
keytool -genkeypair -v -keystore fss_release.jks -alias fss -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 fss_release.jks   # 이 출력이 RELEASE_KEYSTORE_BASE64
```

배포 APK는 이 키로만 서명되고, 앱은 업데이트 때 같은 서명인지 확인한 뒤에만 설치합니다. 안드로이드도 서명이 다른 APK는 덮어 설치하지 않으므로, 다른 사람이 저장소를 복사해 만든 APK는 업데이트로 들어오지 못합니다.

## 검사품질관리 매뉴얼·서식(내부 자료)

원문(HWP·PDF)은 저장소에 넣지 않습니다. `secure/assets/library.bin`은 AES-256-GCM으로 암호화한 묶음이고, 앱이 처음 열 때 `LIBRARY_KEY`로 풀어 앱 전용 저장소에만 둡니다(백업·기기 이전 제외). 공개 저장소에서는 암호문만 보입니다. 다만 APK 안에는 열쇠도 함께 들어가므로, APK를 분석하면 꺼낼 수 있습니다. APK는 공개 릴리스 대신 직접 전달하는 것을 권합니다.

매뉴얼·서식이 바뀌면 다시 묶습니다(필요: `pdftotext`, `pip install cryptography`).

```
python3 tools/pack_library.py --manual-pdf 매뉴얼.pdf --forms-pdf 서식.pdf --forms-hwp 서식.hwp --key "$LIBRARY_KEY"
```

PDF는 한글(한컴오피스)에서 'PDF로 저장'한 파일이 원본과 가장 같습니다.

## 빌드

```
./gradlew :law-core:test :app:assembleRelease
```

`build.yml`이 푸시마다 테스트 APK(아티팩트 `FSS_Helper-test-apk`)를 만듭니다. 배포는 저장소 주인이 `v*` 태그를 올릴 때 `release.yml`이 하며, 배포 서명 키가 없으면 실패합니다.

만든이: 이현덕 with Claude (hdlee73@gmail.com, https://hdlee73.github.io/Home/)
