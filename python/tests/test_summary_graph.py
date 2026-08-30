# -*- coding: utf-8 -*-
"""总结 Agent 子图单测：主图 ingest→review→output + 子图 audit→decide→clarify/history/draft→lessons。

- 用 InMemorySaver 替代 PostgresSaver（无 DB 依赖，CI 可跑；中断/resume 语义一致）
- mock _llm_json（decide/lessons 的结构化输出）与 _model（draft 的报告生成）
"""
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from langgraph.checkpoint.memory import InMemorySaver

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from agent_draft import summary_graph


def base_input(**kw):
    data = {
        "activity": {"id": "t1", "content": "篮球友谊赛", "planned_time": "2026-09-01", "planned_location": "体育馆", "creator_name": "发起人"},
        "signup": {"total": 20, "participate": 15, "not_participate": 5, "online_assist": 2, "not_interested": 1},
        "attendance": {"expected": 15, "present": 13},
        "record": {"submitted": 12, "coverage": 0.8, "avg_score": 82.5, "avg_ai_score": 78.0, "missing": []},
        "reward": {"level_dist": {"金牌": 2}, "adopted_suggestions": 3, "top_score": 95},
        "discussion": {"message_count": 40, "quality_rate": 0.85, "high_freq_count": 3},
        "experience": {"water": 3, "items": []},
    }
    data.update(kw)
    return data


@pytest.fixture(autouse=True)
def mem_saver():
    """每个用例独立 InMemorySaver（无 DB；thread_id 隔离由用例控制）。"""
    with patch.object(summary_graph, "get_saver", return_value=InMemorySaver()):
        yield


def fake_report(content="## 活动概况\n测试报告正文"):
    m = MagicMock()
    m.content = content
    return m


class TestSummaryGraph:
    def test_ready_path(self):
        """无异常信号：decide=ready → draft → lessons → success。"""
        with patch.object(summary_graph, "_llm_json", side_effect=[
            {"decision": "ready", "questions": []},
            [{"category": "总结教训", "title": "场地需提前预约", "content": "上届场地冲突，提前两周预约。"}],
        ]), patch.object(summary_graph, "_model") as model:
            model.return_value.invoke.return_value = fake_report()
            r = summary_graph.summarize("t-ready", base_input())

        assert r["status"] == "success"
        assert "测试报告" in r["report"]["report_text"]
        assert r["report"]["metrics"]["signup"]["total"] == 20  # 指标快照原样回传
        assert len(r["lessons"]) == 1
        assert r["lessons"][0]["title"] == "场地需提前预约"

    def test_clarify_then_resume(self):
        """覆盖率低触发 clarify 中断 → awaiting + questions；resume 注入 answers 恢复 → success。"""
        inp = base_input(record={"submitted": 4, "coverage": 0.2, "avg_score": 80.0, "avg_ai_score": 75.0, "missing": ["张三"]})

        # 首次生成：decide 返回 clarify + 问题 → 中断
        with patch.object(summary_graph, "_llm_json", side_effect=[
            {"decision": "clarify", "questions": [{"id": "q1", "question": "覆盖率为何低？"}]},
            [{"category": "总结教训", "title": "留痕需提醒", "content": "应提前强调留痕要求。"}],
        ]), patch.object(summary_graph, "_model") as model:
            model.return_value.invoke.return_value = fake_report()
            r1 = summary_graph.summarize("t-clarify", inp)

        assert r1["status"] == "awaiting"
        assert r1["questions"][0]["id"] == "q1"

        # resume：answers 注入后 decide 不再 clarify（防死锁），走 ready 完成
        with patch.object(summary_graph, "_llm_json", side_effect=[
            {"decision": "ready", "questions": []},
            [{"category": "筹备知识", "title": "组织经验", "content": "下次提前通知。"}],
        ]), patch.object(summary_graph, "_model") as model:
            model.return_value.invoke.return_value = fake_report()
            r2 = summary_graph.resume("t-clarify", {"q1": "系统上线晚，成员未及时提交"})

        assert r2["status"] == "success"
        assert r2["report"]["report_text"]
        assert r2["lessons"]

    def test_llm_failure_fallback(self):
        """LLM 全部失败：decide 默认 ready、draft 降级结构化摘要、lessons 兜底——仍 success 不崩。"""
        # _llm_json 内部容错返回默认值（decide=ready / lessons=[]），draft 的 _model 抛异常走降级
        with patch.object(summary_graph, "_llm_json", side_effect=[
            {"decision": "ready", "questions": []}, []  # decide → lessons
        ]), patch.object(summary_graph, "_model") as model:
            model.return_value.invoke.side_effect = Exception("llm down")
            r = summary_graph.summarize("t-fallback", base_input(record={"submitted": 2, "coverage": 0.1, "avg_score": 70.0, "avg_ai_score": 60.0, "missing": ["李四"]}))

        assert r["status"] == "success"
        assert "结构化数据摘要" in r["report"]["report_text"]  # draft 降级
        assert r["lessons"]  # lessons 兜底非空
        assert r["lessons"][0]["category"] in ("筹备知识", "总结教训", "context")

    def test_audit_signals_normal_attendance_not_alert(self):
        """到场率 13/15 属正常波动：audit 不产出异常信号（避免过度澄清）。"""
        state = {"input": base_input()}
        out = summary_graph.audit(state)
        signals = out["audit"]["signals"]
        assert not any("到场" in s for s in signals)
        assert not any("覆盖率" in s for s in signals)  # coverage=0.8 正常

    def test_audit_signals_low_coverage(self):
        """覆盖率 0.2 → 审计产出异常信号（触发 clarify 的依据）。"""
        state = {"input": base_input(record={"submitted": 4, "coverage": 0.2, "avg_score": 80.0, "avg_ai_score": 75.0, "missing": ["张三"]})}
        out = summary_graph.audit(state)
        assert any("覆盖率" in s for s in out["audit"]["signals"])