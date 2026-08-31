"""S5 内部密钥中间件测试：配置 secret 时无头/错头 401、对头放行；未配置时跳过校验。"""
import os
import sys
from pathlib import Path

# CI 无 .env：config 模块级 _require 需要这些变量（必须在 import agent_draft 之前设置）
for _k, _v in {
    "LLM_BASE_URL": "http://localhost:9999/v1",
    "LLM_API_KEY": "test-key",
    "LLM_MODEL": "test-model",
    "SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost:5432/club_agent",
    "SPRING_DATASOURCE_USERNAME": "postgres",
    "SPRING_DATASOURCE_PASSWORD": "postgres",
    "AI_DRAFT_INTERNAL_SECRET": "ci-test-secret",
}.items():
    os.environ.setdefault(_k, _v)

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient

from agent_draft import config
from agent_draft.main import app


def test_configured_secret_rejects_missing_and_wrong_header():
    """配置了 secret：不带头 / 带错头 → 401；带对头 → 通过中间件（404 = 路由不存在，说明已放行）"""
    config.AI_DRAFT_INTERNAL_SECRET = "test-secret-2026"
    client = TestClient(app)

    r1 = client.get("/no-such-route")
    assert r1.status_code == 401, "不带 X-Internal-Secret 应 401"

    r2 = client.get("/no-such-route", headers={"X-Internal-Secret": "wrong"})
    assert r2.status_code == 401, "错误 secret 应 401"

    r3 = client.get("/no-such-route", headers={"X-Internal-Secret": "test-secret-2026"})
    assert r3.status_code == 404, "正确 secret 应通过中间件（404 说明到路由层）"


def test_health_always_open():
    """健康检查永远放行（Java 探活不携带内部密钥）"""
    config.AI_DRAFT_INTERNAL_SECRET = "test-secret-2026"
    client = TestClient(app)

    r = client.get("/health")
    assert r.status_code == 200


def test_unconfigured_secret_skips_check():
    """未配置 secret：跳过校验（本地开发无摩擦）"""
    config.AI_DRAFT_INTERNAL_SECRET = ""
    client = TestClient(app)

    r = client.get("/no-such-route")
    assert r.status_code == 404, "未配置 secret 时应放行（404 说明到路由层）"
