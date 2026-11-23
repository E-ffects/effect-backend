#!/usr/bin/env python3
"""스테이징의 Spring API를 읽기 점검하고, 요청 시 테스트 RMA 한 건을 끝까지 처리함."""

import argparse
import hashlib
import json
import os
import re
import sys
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class CheckError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise CheckError(message)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # 다른 주소로 이동하라는 응답에 로그인 정보나 토큰을 보내지 않음
        return None


class Api:
    def __init__(self, base_url, timeout):
        self.base_url = base_url
        self.timeout = timeout
        self.opener = build_opener(NoRedirect())

    def request(self, path, *, http_method="GET", token=None, payload=None, headers=None, raw=False, expected=200):
        request_headers = dict(headers or {})
        if token:
            request_headers["Authorization"] = f"Bearer {token}"
        if isinstance(payload, dict):
            request_headers["Content-Type"] = "application/json"
            payload = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = Request(f"{self.base_url}{path}", data=payload, headers=request_headers, method=http_method)
        endpoint = f"{http_method} {path.split('?')[0]}"
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                status, body = response.status, response.read()
        except HTTPError as error:
            with error:
                status, body = error.code, error.read()
            if status == expected and status >= 400:
                return None
        except (URLError, TimeoutError, OSError) as error:
            raise CheckError(f"{endpoint}: 연결 실패 ({type(error).__name__})") from None
        if status != expected:
            # 실패 응답에 접수 원문·개인정보가 들어갈 수 있어 공통 오류 코드만 출력함
            raise CheckError(f"{endpoint}: HTTP {status}, {error_code(body)}")
        if raw:
            return body
        try:
            envelope = json.loads(body)
            if isinstance(envelope, dict) and envelope.get("code") == "SUCCESS" and "content" in envelope:
                return envelope["content"]
        except ValueError:
            pass
        raise CheckError(f"{endpoint}: Spring 공통 응답 형식 확인 실패")


def error_code(body):
    try:
        value = json.loads(body)
        code = value.get("code") if isinstance(value, dict) else None
        if isinstance(code, str) and re.fullmatch(r"[A-Z][A-Z0-9_]{0,79}", code):
            return code
    except ValueError:
        pass
    return "오류 코드 없음"


def login(api, role):
    username, password = (os.environ.get(f"STAGING_{role}_{field}") for field in ("USERNAME", "PASSWORD"))
    require(username and password, f"STAGING_{role}_USERNAME 및 STAGING_{role}_PASSWORD 필요")
    token = api.request("/api/auth/login", http_method="POST",
                        payload={"username": username, "password": password})["token"]
    me = api.request("/api/auth/me", token=token)
    require(me["role"] == role, f"{role} 테스트 계정의 역할이 다름")
    return token


def rma_path(rma_id):
    return f"/api/rmas/{quote(rma_id, safe='')}"


def preflight(api, sample_id):
    api.request("/api/auth/me", expected=401)
    cs, quality = login(api, "CS"), login(api, "QUALITY")
    print("PASS 로그인 및 CS·QUALITY 역할", flush=True)
    health = api.request("/api/ai/health", token=cs)
    require(health["available"] and health.get("provider") != "mock", "실제 FastAPI 사용 가능 상태를 확인하지 못함")
    meta = api.request("/api/meta", token=cs)
    require(meta["customers"] and meta["products"], "기준 고객사 또는 제품이 없음")
    print("PASS FastAPI 상태 및 v5 기준 정보", flush=True)
    sample = api.request(rma_path(sample_id), token=quality)
    require(sample["rma_id"] == sample_id, "검사용 케이스를 찾지 못함")
    cs_sample = api.request(rma_path(sample_id), token=cs)
    require("qa_status" not in cs_sample and "approval_status" not in cs_sample, "CS에 내부 판정 상태가 노출됨")
    api.request("/api/approvals", token=cs, expected=403)
    api.request("/api/approvals?size=1", token=quality)
    print(f"PASS 기존 케이스 {sample_id} 조회 및 역할 제한", flush=True)
    return cs, quality, sample


def multipart(fields, raw):
    boundary = uuid.uuid4().hex
    delimited = []
    for key, value in fields.items():
        delimited.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    file_header = (f'--{boundary}\r\nContent-Disposition: form-data; name="raw_file"; filename="staging-smoke.txt"'
                   "\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n")
    delimited.append(file_header.encode())
    return b"".join(delimited) + raw + f"\r\n--{boundary}--\r\n".encode(), boundary


def create_intake(api, cs, sample):
    run_id = uuid.uuid4().hex
    fields = {
        "customer_id": sample["customer"]["customer_id"],
        "reported_part_no": sample["reported_part_no"],
        "occurred_at": sample["occurred_at"][:10],
        "detection_stage": sample["detection_stage"],
        "claimed_defect_qty": 2,
        "input_qty": 10,
    }
    if sample.get("reported_lot_no"):
        fields["reported_lot_no"] = sample["reported_lot_no"]
    # 시연 데이터의 제품·Lot 기준으로 가상 불량 문구를 조립함
    raw = (f"[STAGING SMOKE {run_id[:8]}] 테스트용 반품 접수\n"
           f"P/N {fields['reported_part_no']} Lot {fields.get('reported_lot_no', '')}\n"
           "ICT 기능검사에서 인접 패턴 사이 쇼트가 반복 검출되었습니다. "
           "검사 수량 10개 중 불량 2개, 절연저항 저하가 확인됩니다. 동일 Lot 선별을 요청드립니다.\n").encode()
    body, boundary = multipart(fields, raw)
    headers = {"Idempotency-Key": f"staging-smoke-{run_id}", "Content-Type": f"multipart/form-data; boundary={boundary}"}
    request = dict(http_method="POST", token=cs, payload=body, headers=headers)
    intake = api.request("/api/intake", **request)
    rma_id = intake["rma_id"]
    print(f"PASS 테스트 접수 {rma_id} 생성", flush=True)
    duplicate = api.request("/api/intake", **request)
    require(duplicate["rma_id"] == rma_id, "같은 접수 요청을 다시 보냈을 때 RMA 번호가 바뀜")
    return intake, raw


def attachment_bytes(api, path, token):
    items = api.request(f"{path}/attachments", token=token)["items"]
    raw_files = [item for item in items if item["kind"] == "RAW"]
    require(len(raw_files) == 1, "원문 첨부가 한 개여야 함")
    return api.request(f"/api/attachments/{raw_files[0]['attachment_id']}", token=token, raw=True)


def wait_analysis(api, path, quality, timeout):
    deadline = time.monotonic() + timeout
    while True:
        analysis = api.request(f"{path}/analysis", token=quality)
        state = analysis["analysis_state"]
        if state in {"SUCCEEDED", "PARTIAL"}:
            return analysis
        require(state in {"QUEUED", "RUNNING"}, f"분석 진행 실패: {state}")
        remaining = deadline - time.monotonic()
        require(remaining > 0, "분석 완료 대기 시간 초과")
        time.sleep(min(1, remaining))


def run_e2e(api, cs, quality, sample, timeout):
    intake, raw = create_intake(api, cs, sample)
    rma_id = intake["rma_id"]
    path = rma_path(rma_id)
    require(attachment_bytes(api, path, quality) == raw, "업로드·다운로드 원문이 다름")
    print("PASS 첨부 저장·다운로드 및 중복 접수 방지", flush=True)
    if intake["intake_state"] == "TRIAGE_REVIEW":
        proposal = intake["triage"]
        api.request(f"{path}/triage", http_method="PUT", token=quality,
                    payload={key: proposal.get(key) for key in ("severity", "characteristic_symbol", "analysis_level", "rationale")})
    else:
        require(intake["intake_state"] == "ACCEPTED", f"접수 분류·QA 실패: {intake['intake_state']}")
    result = wait_analysis(api, path, quality, timeout)
    analysis_id = result["analysis_id"]
    candidate = next((row for row in result["result"]["cause_candidates"] if row.get("cause_code")), None)
    require(candidate, "채택할 분석 원인 후보가 없음")
    cs_analysis = api.request(f"{path}/analysis", token=cs)
    require("cause_candidates" not in cs_analysis["result"], "CS 응답에 내부 원인 후보가 노출됨")
    print(f"PASS QA 판정 및 분석 {result['analysis_state']}", flush=True)
    # 원인 판정은 분석 시작 때 등급 대신 상세의 최신 QA 등급을 기준으로 함
    level = api.request(path, token=quality)["triage"]["analysis_level"]
    require(level in {"L1", "L2", "L3"}, "QA 등급 확인 실패")
    decision = "APPROVED" if level == "L3" else "CONFIRMED"
    recorded = api.request(f"{path}/cause-decision", http_method="POST", token=quality, payload={
        "analysis_id": analysis_id, "decision": decision, "comment": "[STAGING SMOKE] 테스트 원인 판정",
        "candidates": [{"cause_code": candidate["cause_code"], "status": "ADOPTED"}],
    })
    require(recorded["approval_status"] == decision, "원인 판정 저장 실패")
    print(f"PASS 원인 판정 {decision}", flush=True)
    reply = api.request(f"{path}/communications", http_method="POST", token=cs, payload={
        "analysis_id": analysis_id, "source": "MANUAL", "message_text": "[STAGING SMOKE] 테스트 반품 처리 결과를 안내드립니다.",
    })
    require(reply["cs_status"] == "RESPONDED", "CS 회신 저장 실패")
    action = api.request(f"{path}/service-action", http_method="POST", token=cs, payload={
        "action_type": "REPLACEMENT_SHIPMENT", "board_qty": 2, "result": "EXECUTED",
    })
    require(action["cs_status"] == "CLOSED" and action["executed_at"], "CS 종료 상태 또는 실행 시각 확인 실패")
    digest = hashlib.sha256(raw).hexdigest()
    verify_case(api, quality, rma_id, digest)
    print(f"재시작 후 확인용: --verify-rma {rma_id} --attachment-sha256 {digest}", flush=True)


def verify_case(api, quality, rma_id, expected_digest):
    path = rma_path(rma_id)
    detail = api.request(path, token=quality)
    require(detail["cs_status"] == "CLOSED" and detail["approval_status"] in {"APPROVED", "CONFIRMED"},
            "저장된 종료·원인 판정 상태가 다름")
    analysis = api.request(f"{path}/analysis", token=quality)
    require(analysis["analysis_state"] in {"SUCCEEDED", "PARTIAL"} and analysis["result"], "저장된 분석 결과 없음")
    messages = api.request(f"{path}/communications", token=quality)["items"]
    require(any(row["author_role"] == "CS" for row in messages), "저장된 CS 회신 없음")
    digest = hashlib.sha256(attachment_bytes(api, path, quality)).hexdigest()
    if expected_digest:
        require(digest == expected_digest, "저장된 원문 해시가 다름")
    print(f"PASS {rma_id} 종료·원인 판정·분석 결과·CS 회신·원문 조회", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="스테이징 주소 (예: https://staging.example.com)")
    parser.add_argument("--sample-rma", default="RMA-08927", help="v5 합성 데이터의 조회·E2E 기준 접수")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--e2e", action="store_true", help="합성 테스트 RMA 한 건을 생성하고 종료함")
    mode.add_argument("--verify-rma", help="앞서 종료한 테스트 RMA를 다시 읽음")
    parser.add_argument("--attachment-sha256", help="앞서 출력된 원문 해시")
    parser.add_argument("--analysis-timeout", type=int, default=60)
    args = parser.parse_args()
    try:
        url = urlsplit(args.base_url)
        is_local = url.hostname in {"127.0.0.1", "localhost", "::1"}
        require(bool(url.hostname) and (url.scheme == "https" or (url.scheme == "http" and is_local))
                and not (url.username or url.password or url.query or url.fragment or url.path.rstrip("/")),
                "서버 주소만 지정하세요. 원격은 HTTPS를 사용합니다.")
        require(args.analysis_timeout > 0, "분석 대기 시간은 양수여야 함")
        require(not args.attachment_sha256 or (
                args.verify_rma and re.fullmatch(r"[0-9a-f]{64}", args.attachment_sha256)),
                "원문 해시는 --verify-rma와 함께 64자리 소문자 16진수로 지정하세요.")
        api = Api(args.base_url.rstrip("/"), timeout=30)
        cs, quality, sample = preflight(api, args.sample_rma)
        if args.e2e:
            run_e2e(api, cs, quality, sample, args.analysis_timeout)
        elif args.verify_rma:
            verify_case(api, quality, args.verify_rma, args.attachment_sha256)
        print("스테이징 점검 통과", flush=True)
        return 0
    except CheckError as error:
        print(f"FAIL {error}", file=sys.stderr)
        return 1
    except (ValueError, KeyError, TypeError, AttributeError):
        print("FAIL 응답에 필수 필드가 없거나 형식이 다름", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
