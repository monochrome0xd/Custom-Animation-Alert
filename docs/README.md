# GitHub Pages 설정 가이드

이 폴더가 `https://monochrome-x.github.io/Custom-Animation-Alert/` 의 공개 사이트입니다.

## GitHub Pages 활성화 절차

1. GitHub에 push (`git add docs && git commit -m "docs: add privacy policy" && git push`)
2. https://github.com/Monochrome-X/Custom-Animation-Alert/settings/pages 접속
3. **Build and deployment** 섹션에서:
   - **Source**: `Deploy from a branch`
   - **Branch**: `main` (또는 `master`) 선택, 폴더는 **`/docs`** 선택
4. Save 클릭
5. 1-2분 후 다음 URL 활성화:
   - 홈: `https://monochrome-x.github.io/Custom-Animation-Alert/`
   - 개인정보처리방침: `https://monochrome-x.github.io/Custom-Animation-Alert/privacy-policy.html`

## Play Console에 입력할 URL

```
https://monochrome-x.github.io/Custom-Animation-Alert/privacy-policy.html
```

## 출시 전 마지막 체크리스트

- [ ] `privacy-policy.html` 안의 `CONTACT_EMAIL_PLACEHOLDER`를 실제 메일로 교체
- [ ] GitHub Pages 활성화 후 URL 접속 → 페이지 정상 표시 확인
- [ ] Play Console에 위 URL 입력 (앱 콘텐츠 → 개인정보처리방침)
