"""Host side of LostFoundReviewDeviceTest; only synthetic local test records."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--url', default='http://127.0.0.1:8792')
    parser.add_argument('--credentials', type=Path, default=Path(os.environ.get(
        'MY_CHUANNONG_LOCAL_CREDENTIALS', str(Path.home() / '.codex/private/my-chuannong/backend-local.json'))))
    parser.add_argument('--report-dir', type=Path, required=True)
    parser.add_argument('--timeout', type=int, default=300)
    args = parser.parse_args()
    if urllib.parse.urlsplit(args.url).hostname not in ('127.0.0.1', 'localhost'):
        parser.error('This test driver only supports the local backend.')
    args.report_dir.mkdir(parents=True, exist_ok=True)
    remote = '/sdcard/Android/data/com.mychuannong.app.lostreviewtest/files/'

    def read(name):
        result = subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'cat', remote + name],
                                capture_output=True, timeout=5)
        try:
            return json.loads(result.stdout)
        except (ValueError, UnicodeError):
            return {}

    def api(method, path, body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(args.url.rstrip('/') + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=8) as response:
            return json.loads(response.read())['data']

    credentials = json.loads(args.credentials.read_text(encoding='utf-8'))
    token = api('POST', '/api/v1/auth/login', {
        'username': credentials['adminUsername'], 'password': credentials['adminPassword']})['token']
    del credentials
    previous = read('lost-review-device-result.json')
    previous_run = previous.get('run') if previous.get('passed') else None
    seen, records = set(), []
    deadline = time.monotonic() + args.timeout
    print('Ready; start LostFoundReviewDeviceTest in the isolated install.', flush=True)
    while time.monotonic() < deadline:
        stage = read('lost-review-stage.json')
        key = (stage.get('run'), stage.get('sequence'))
        if stage and stage.get('run') != previous_run and key not in seen:
            action, ident = stage.get('action'), stage.get('id')
            if action not in ('approve', 'reject', 'take_down', 'delete'):
                raise RuntimeError('Unexpected test action')
            path = '/api/v1/admin/campus/lost-found/' + str(ident)
            try:
                row = api('GET', path, token=token)
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    seen.add(key)
                    continue
                raise
            if not row['title'].startswith('失物验收-') or not row['title'].startswith(stage['run']):
                raise RuntimeError('Refusing to operate on non-test content')
            result = api('DELETE' if action == 'delete' else 'PATCH', path,
                None if action == 'delete' else {
                    'action': action, 'expectedUpdatedAt': row['updatedAt'],
                    'reason': '' if action == 'approve' else '验收测试：请补充真实描述与清晰图片。'}, token)
            records.append({'run': stage['run'], 'sequence': stage['sequence'],
                            'action': action, 'id': ident, 'result': result})
            seen.add(key)
            (args.report_dir / 'device-admin-actions.json').write_text(
                json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
            print(f"Applied synthetic action {stage['sequence']}: {action}", flush=True)
        result = read('lost-review-device-result.json')
        if result.get('passed') and result.get('run') != previous_run and result.get('run') == stage.get('run'):
            (args.report_dir / 'device-result.json').write_text(
                json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
            print('Device workflow passed.', flush=True)
            return
        time.sleep(0.6)
    raise RuntimeError('Timed out waiting for device workflow')


if __name__ == '__main__':
    main()
