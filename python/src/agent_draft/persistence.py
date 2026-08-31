"""PostgresSaver 检查点：会话续聊恢复（thread_id = concept_id）。

业务事实源是 Java 侧 concept_draft_session 表；checkpoint 只是运行态缓存，
服务重启后可从检查点恢复对话上下文。
"""
import logging
import threading

import psycopg
import psycopg_pool
from langgraph.checkpoint.postgres import PostgresSaver

from . import config

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_pool: psycopg_pool.ConnectionPool | None = None
_saver: PostgresSaver | None = None


def get_saver() -> PostgresSaver:
    """懒加载单例：psycopg 连接池（断线自动重连，DB 重启后服务可自愈）+
    双检锁防多线程首次并发重复初始化（全量审查 P2/P3 修复）。"""
    global _pool, _saver
    if _saver is None:
        with _lock:
            if _saver is None:
                _pool = psycopg_pool.ConnectionPool(
                    config.DB_URL,
                    min_size=1,
                    max_size=4,
                    open=False,
                    kwargs={
                        "autocommit": True,
                        "prepare_threshold": 0,
                        "row_factory": psycopg.rows.dict_row,
                    },
                )
                _pool.open()
                _saver = PostgresSaver(_pool)
                _saver.setup()  # 幂等：建 checkpoint 相关表（与 agent_draft 共用 checkpoint 表）
                logger.info("PostgresSaver 就绪（连接池 min=1 max=4）：%s", config.DB_URL.split("@")[-1])
    return _saver
