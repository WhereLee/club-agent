"""PostgresSaver 检查点：问答会话续聊恢复（thread_id = qa_session_id）。

业务事实源是 Java 侧 qa_message 表；checkpoint 只是运行态缓存，
服务重启后可从检查点恢复对话上下文。
"""
import logging

import psycopg
from langgraph.checkpoint.postgres import PostgresSaver

from . import config

logger = logging.getLogger(__name__)

_saver: PostgresSaver | None = None


def get_saver() -> PostgresSaver:
    """懒加载单例（模式同 agent_draft.persistence：常驻连接 + autocommit）。"""
    global _saver
    if _saver is None:
        conn = psycopg.connect(
            config.DB_URL,
            autocommit=True,
            prepare_threshold=0,
            row_factory=psycopg.rows.dict_row,
        )
        _saver = PostgresSaver(conn)
        _saver.setup()  # 幂等：建 checkpoint 相关表（与 agent_draft 共用 checkpoint 表）
        logger.info("PostgresSaver 就绪：%s", config.DB_URL.split("@")[-1])
    return _saver
