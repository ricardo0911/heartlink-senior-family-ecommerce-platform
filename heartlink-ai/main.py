"""
连心选 HeartLink AI 服务
Python FastAPI 微服务 - 集成智谱AI GLM大模型
"""

import base64
import ipaddress
import io
import math
import oswol
import struct
import uuid
import wave
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import urlparse

import httpx
import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, File, Form, HTTPException, Request, Response, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

# 加载环境变量
BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")
load_dotenv(BASE_DIR / ".env.local", override=True)

# 智谱AI配置
AI_BASE_URL = (
    os.getenv("AI_BASE_URL")
    or os.getenv("OPENAI_BASE_URL")
    or os.getenv("DEEPSEEK_BASE_URL")
    or "https://api.deepseek.com/v1"
)
AI_API_KEY = (
    os.getenv("AI_API_KEY")
    or os.getenv("OPENAI_API_KEY")
    or os.getenv("DEEPSEEK_API_KEY")
    or os.getenv("ZHIPU_API_KEY")
)
AI_MODEL = (
    os.getenv("AI_MODEL")
    or os.getenv("OPENAI_MODEL")
    or os.getenv("DEEPSEEK_MODEL")
    or "deepseek-chat"
)
AI_VISION_MODEL = (
    os.getenv("AI_VISION_MODEL")
    or os.getenv("OPENAI_VISION_MODEL")
    or os.getenv("DEEPSEEK_VISION_MODEL")
    or AI_MODEL
)
TTS_API_URL = os.getenv("TTS_API_URL")
TTS_API_KEY = os.getenv("TTS_API_KEY")

# 尝试导入智谱AI
def _normalize_base_url(raw_url: str) -> str:
    base = (raw_url or "").rstrip("/")
    if not base:
        return "https://api.deepseek.com/v1"
    return base if base.endswith("/v1") else f"{base}/v1"


AI_BASE_URL = _normalize_base_url(AI_BASE_URL)
AI_ENABLED = bool(AI_API_KEY)
DASHSCOPE_BASE_URL = (os.getenv("DASHSCOPE_BASE_URL") or "https://dashscope.aliyuncs.com/api/v1").rstrip("/")
DASHSCOPE_API_KEY = (os.getenv("DASHSCOPE_API_KEY") or os.getenv("TRYON_API_KEY") or "").strip()
TRYON_MODEL = (os.getenv("TRYON_MODEL") or "aitryon").strip()
TRYON_PARSING_MODEL = (os.getenv("TRYON_PARSING_MODEL") or "aitryon-parsing-v1").strip()
MEDIA_BASE_URL = (
    os.getenv("MEDIA_BASE_URL")
    or os.getenv("BACKEND_MEDIA_BASE_URL")
    or "http://127.0.0.1:8089"
).rstrip("/")
GENERATED_DIR = BASE_DIR / "generated"
TRYON_INPUT_DIR = GENERATED_DIR / "tryon-inputs"
TRYON_OUTPUT_DIR = GENERATED_DIR / "tryon"
GENERATED_DIR.mkdir(parents=True, exist_ok=True)
TRYON_INPUT_DIR.mkdir(parents=True, exist_ok=True)
TRYON_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
ALLOWED_IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".heic", ".webp"}
LOCAL_HOSTNAMES = {"localhost", "127.0.0.1", "0.0.0.0", "::1"}
LOCAL_MEDIA_PATH_PREFIXES = ("/upload/", "/generated/", "/static/")

app = FastAPI(
    title="HeartLink AI Service",
    description="连心选 AI 微服务 - 健康分析、评论总结、语音合成、健康周报、以图搜物（集成大模型 AI）",
    version="3.0.0"
)

# CORS配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount("/generated", StaticFiles(directory=GENERATED_DIR), name="generated")


def _normalize_image_suffix(raw_suffix: str, fallback: str = ".jpg") -> str:
    suffix = str(raw_suffix or "").strip().lower()
    if suffix and not suffix.startswith("."):
        suffix = f".{suffix}"
    return suffix if suffix in ALLOWED_IMAGE_SUFFIXES else fallback


def _is_public_http_url(url: str) -> bool:
    try:
        parsed = urlparse(str(url or "").strip())
    except Exception:
        return False

    if parsed.scheme not in {"http", "https"}:
        return False

    host = (parsed.hostname or "").strip().lower()
    if not host or host in LOCAL_HOSTNAMES:
        return False

    try:
        address = ipaddress.ip_address(host)
        return not (
            address.is_private
            or address.is_loopback
            or address.is_link_local
            or address.is_multicast
            or address.is_reserved
        )
    except ValueError:
        return "." in host


def _is_allowed_local_media_url(url: str) -> bool:
    try:
        parsed = urlparse(str(url or "").strip())
    except Exception:
        return False

    if parsed.scheme not in {"http", "https"}:
        return False

    host = (parsed.hostname or "").strip().lower()
    path = parsed.path or "/"
    if not host or not path.startswith(LOCAL_MEDIA_PATH_PREFIXES):
        return False

    if host in LOCAL_HOSTNAMES:
        return True

    try:
        address = ipaddress.ip_address(host)
        return bool(address.is_private or address.is_loopback)
    except ValueError:
        return False


def _normalize_media_source_text(source_text: str) -> str:
    text = str(source_text or "").strip().replace("\\", "/")
    if not text:
        return ""

    for marker in ("/static/", "static/"):
        index = text.find(marker)
        if index >= 0:
            return f"/{text[index:].lstrip('/')}"

    for marker in ("/upload/", "upload/"):
        index = text.find(marker)
        if index >= 0:
            return f"/{text[index:].lstrip('/')}"

    for marker in ("/generated/", "generated/"):
        index = text.find(marker)
        if index >= 0:
            return f"/{text[index:].lstrip('/')}"

    return text


def _build_client_asset_url(request: Request, relative_path: str) -> str:
    return f"{str(request.base_url).rstrip('/')}{relative_path}"


def _write_binary_file(target: Path, content: bytes) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)
    return target


def _resolve_child_static_asset(path_text: str) -> Path:
    relative_path = path_text.lstrip("/")
    candidate = (BASE_DIR.parent / "heartlink-app-child" / "src" / relative_path).resolve()
    static_root = (BASE_DIR.parent / "heartlink-app-child" / "src" / "static").resolve()
    if static_root not in candidate.parents and candidate != static_root:
        raise HTTPException(status_code=400, detail="服饰图片路径不合法")
    if not candidate.is_file():
        raise HTTPException(status_code=404, detail="未找到服饰图片资源")
    return candidate


async def _download_binary(url: str) -> Tuple[bytes, str]:
    try:
        async with httpx.AsyncClient(timeout=90.0, follow_redirects=True) as client:
            response = await client.get(url)
            response.raise_for_status()
    except httpx.TimeoutException as exc:
        raise HTTPException(status_code=504, detail="下载图片超时，请确认图片地址稳定可访问") from exc
    except httpx.HTTPStatusError as exc:
        raise HTTPException(status_code=400, detail="图片地址不可访问，请检查图片链接是否有效") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail="下载图片失败，请稍后重试并确认网络可访问该图片地址") from exc

    resolved_path = urlparse(str(response.url)).path or urlparse(url).path
    suffix = _normalize_image_suffix(Path(resolved_path).suffix)
    return response.content, suffix


async def _save_upload_as_tryon_source(
    upload: UploadFile,
    request_id: str,
    slot: str
) -> str:
    content = await upload.read()
    if not content:
        raise HTTPException(status_code=400, detail="上传的模特图片为空")
    suffix = _normalize_image_suffix(Path(upload.filename or "").suffix)
    file_name = f"{request_id}-{slot}{suffix}"
    return await _upload_bytes_to_dashscope_oss(content, file_name, TRYON_MODEL)


async def _materialize_tryon_image(
    source: str,
    request_id: str,
    slot: str
) -> str:
    source_text = _normalize_media_source_text(source)
    if not source_text:
        return ""

    if source_text.startswith("oss://"):
        return source_text

    if source_text.startswith("http://") or source_text.startswith("https://"):
        if _is_public_http_url(source_text) or _is_allowed_local_media_url(source_text):
            content, suffix = await _download_binary(source_text)
            file_name = f"{request_id}-{slot}{suffix}"
            return await _upload_bytes_to_dashscope_oss(content, file_name, TRYON_MODEL)
        raise HTTPException(status_code=400, detail="图片 URL 必须为公网可访问的 HTTP/HTTPS 地址")

    if source_text.startswith("/static/") or source_text.startswith("static/"):
        source_path = _resolve_child_static_asset(source_text)
        suffix = _normalize_image_suffix(source_path.suffix)
        file_name = f"{request_id}-{slot}{suffix}"
        return await _upload_bytes_to_dashscope_oss(source_path.read_bytes(), file_name, TRYON_MODEL)

    if source_text.startswith("/upload/") or source_text.startswith("upload/"):
        if not MEDIA_BASE_URL:
            raise HTTPException(status_code=503, detail="未配置 MEDIA_BASE_URL，无法处理上传图片地址")
        source_url = f"{MEDIA_BASE_URL}/{source_text.lstrip('/')}"
        content, suffix = await _download_binary(source_url)
        file_name = f"{request_id}-{slot}{suffix}"
        return await _upload_bytes_to_dashscope_oss(content, file_name, TRYON_MODEL)

    if source_text.startswith("/generated/"):
        local_path = GENERATED_DIR / source_text.lstrip("/").removeprefix("generated/")
        if not local_path.is_file():
            raise HTTPException(status_code=404, detail="未找到试穿输入图片")
        suffix = _normalize_image_suffix(local_path.suffix)
        file_name = f"{request_id}-{slot}{suffix}"
        return await _upload_bytes_to_dashscope_oss(local_path.read_bytes(), file_name, TRYON_MODEL)

    raise HTTPException(status_code=400, detail=f"暂不支持该图片来源: {source_text[:160]}")

    raise HTTPException(status_code=400, detail="暂不支持该图片来源")


def _dashscope_headers(async_mode: bool = False) -> Dict[str, str]:
    if not DASHSCOPE_API_KEY:
        raise HTTPException(status_code=503, detail="未配置 DASHSCOPE_API_KEY，AI试穿不可用")
    headers = {
        "Authorization": f"Bearer {DASHSCOPE_API_KEY}",
        "Content-Type": "application/json",
    }
    if async_mode:
        headers["X-DashScope-Async"] = "enable"
    return headers


def _format_tryon_error(code: str, message: str) -> str:
    normalized_code = str(code or "").strip()
    normalized_message = str(message or "").strip()

    if normalized_code == "InvalidPerson":
        return "模特图不合规，请上传单人、正面、全身、清晰的照片"
    if normalized_code == "InvalidGarment":
        return "缺少服饰图片，请至少提供一张上装或下装图片"
    if normalized_code == "InvalidInputLength":
        return "图片尺寸或文件大小不符合要求，请确保边长在 150-4096 像素且文件大小在 5KB-5MB 之间"
    if normalized_code == "InvalidURL":
        return "图片 URL 无效，请确认图片是公网可访问的 HTTP/HTTPS 地址，且格式为 JPG/PNG/BMP/HEIC/WEBP"
    if normalized_code == "InvalidParameter" and "Download the media resource timed out" in normalized_message:
        return "图片下载超时，请改用稳定的公网图片地址，优先使用中国内地可访问的资源"
    if normalized_code == "InvalidParameter":
        return "试穿请求参数有误，请检查图片和试穿选项"
    if normalized_code == "DataInspectionFailed":
        return "输入图片未通过内容安全检测，请更换模特图或服饰图后重试"
    if normalized_code:
        return f"{normalized_code}: {normalized_message}" if normalized_message else normalized_code
    return normalized_message or "AI试穿调用失败"


async def _dashscope_post_json(
    path: str,
    payload: Dict[str, Any],
    async_mode: bool = False,
    oss_resolve: bool = False
) -> Dict[str, Any]:
    try:
        async with httpx.AsyncClient(timeout=120.0, follow_redirects=True) as client:
            headers = _dashscope_headers(async_mode=async_mode)
            if oss_resolve:
                headers["X-DashScope-OssResourceResolve"] = "enable"
            response = await client.post(
                f"{DASHSCOPE_BASE_URL}{path}",
                headers=headers,
                json=payload,
            )
    except httpx.TimeoutException as exc:
        raise HTTPException(status_code=504, detail="连接 AI 试衣服务超时，请稍后重试") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail="无法连接 AI 试衣服务，请确认服务器网络可访问阿里云 DashScope（中国内地-北京）",
        ) from exc

    try:
        data = response.json()
    except Exception:
        data = {}

    if response.status_code >= 400:
        raise HTTPException(
            status_code=response.status_code,
            detail=_format_tryon_error(data.get("code"), data.get("message"))
        )
    return data


async def _dashscope_get_json(path: str) -> Dict[str, Any]:
    try:
        async with httpx.AsyncClient(timeout=90.0, follow_redirects=True) as client:
            response = await client.get(
                f"{DASHSCOPE_BASE_URL}{path}",
                headers=_dashscope_headers(async_mode=False),
            )
    except httpx.TimeoutException as exc:
        raise HTTPException(status_code=504, detail="查询 AI 试衣任务超时，请稍后重试") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail="无法连接 AI 试衣服务，请确认服务器网络可访问阿里云 DashScope（中国内地-北京）",
        ) from exc

    try:
        data = response.json()
    except Exception:
        data = {}

    if response.status_code >= 400:
        raise HTTPException(
            status_code=response.status_code,
            detail=_format_tryon_error(data.get("code"), data.get("message"))
        )
    return data


async def _get_dashscope_upload_policy(model_name: str) -> Dict[str, Any]:
    try:
        async with httpx.AsyncClient(timeout=60.0, follow_redirects=True) as client:
            response = await client.get(
                f"{DASHSCOPE_BASE_URL}/uploads",
                headers=_dashscope_headers(async_mode=False),
                params={"action": "getPolicy", "model": model_name},
            )
    except httpx.TimeoutException as exc:
        raise HTTPException(status_code=504, detail="获取试衣上传凭证超时，请稍后重试") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail="无法连接 AI 试衣服务，请确认服务器网络可访问阿里云 DashScope（中国内地-北京）",
        ) from exc
    try:
        data = response.json()
    except Exception:
        data = {}
    if response.status_code >= 400:
        raise HTTPException(
            status_code=response.status_code,
            detail=_format_tryon_error(data.get("code"), data.get("message"))
        )
    return data.get("data") or {}


async def _upload_bytes_to_dashscope_oss(content: bytes, file_name: str, model_name: str) -> str:
    policy = await _get_dashscope_upload_policy(model_name)
    upload_key = f"{policy['upload_dir']}/{file_name}"
    files = {
        "OSSAccessKeyId": (None, policy["oss_access_key_id"]),
        "Signature": (None, policy["signature"]),
        "policy": (None, policy["policy"]),
        "x-oss-object-acl": (None, policy["x_oss_object_acl"]),
        "x-oss-forbid-overwrite": (None, policy["x_oss_forbid_overwrite"]),
        "key": (None, upload_key),
        "success_action_status": (None, "200"),
        "file": (file_name, content, "application/octet-stream"),
    }
    try:
        async with httpx.AsyncClient(timeout=120.0, follow_redirects=True) as client:
            response = await client.post(policy["upload_host"], files=files)
    except httpx.TimeoutException as exc:
        raise HTTPException(status_code=504, detail="上传试衣图片超时，请稍后重试") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail="上传试衣图片失败，请确认服务器网络可以访问阿里云 OSS") from exc
    if response.status_code != 200:
        raise HTTPException(status_code=502, detail="试穿图片上传到临时存储失败，请稍后重试")
    return f"oss://{upload_key}"


async def _extract_person_garment(person_image_url: str, clothes_type: str) -> str:
    payload = {
        "model": TRYON_PARSING_MODEL,
        "input": {
            "image_url": person_image_url
        },
        "parameters": {
            "clothes_type": [clothes_type]
        }
    }
    data = await _dashscope_post_json(
        "/services/vision/image-process/process",
        payload,
        async_mode=False,
        oss_resolve=person_image_url.startswith("oss://")
    )
    crop_urls = (((data.get("output") or {}).get("crop_img_url")) or [])
    crop_url = crop_urls[0] if crop_urls else None
    if crop_url:
        return crop_url
    label = "上装" if clothes_type == "upper" else "下装" if clothes_type == "lower" else "服饰"
    raise HTTPException(status_code=400, detail=f"未能从模特图中识别到原有{label}，请更换更清晰的全身正面照")


async def _cache_tryon_result_image(task_id: str, image_url: str) -> Path:
    suffix = _normalize_image_suffix(Path(urlparse(str(image_url)).path).suffix, ".jpg")
    target = TRYON_OUTPUT_DIR / task_id / f"result{suffix}"
    if target.is_file():
        return target
    content, _ = await _download_binary(image_url)
    return _write_binary_file(target, content)


def _normalize_tryon_role(raw_role: str) -> str:
    role = str(raw_role or "").strip().lower()
    if role in {"top", "upper"}:
        return "top"
    if role in {"bottom", "lower"}:
        return "bottom"
    if role in {"dress", "full", "onesie"}:
        return "dress"
    return "top"


def _build_tryon_payload(
    person_image_url: str,
    top_garment_url: str,
    bottom_garment_url: str,
    resolution: int,
    restore_face: bool
) -> Dict[str, Any]:
    if not top_garment_url and not bottom_garment_url:
        raise HTTPException(status_code=400, detail="缺少服饰图片，请至少提供一张上装或下装图片")

    payload: Dict[str, Any] = {
        "model": TRYON_MODEL,
        "input": {
            "person_image_url": person_image_url
        },
        "parameters": {
            "resolution": resolution if resolution in {-1, 1024, 1280} else -1,
            "restore_face": bool(restore_face)
        }
    }

    if top_garment_url:
        payload["input"]["top_garment_url"] = top_garment_url
    if bottom_garment_url:
        payload["input"]["bottom_garment_url"] = bottom_garment_url
    return payload


# ============================================
# 数据模型
# ============================================

class HealthProfile(BaseModel):
    age: Optional[int] = None
    height: Optional[float] = None
    weight: Optional[float] = None
    chronicDiseases: Optional[List[str]] = []
    allergies: Optional[List[str]] = []
    hobbies: Optional[List[str]] = []


class Product(BaseModel):
    id: int
    name: str
    healthTags: Optional[List[str]] = []
    warningTags: Optional[List[str]] = []
    description: Optional[str] = None


class HealthAnalyzeRequest(BaseModel):
    product: Product
    profile: HealthProfile


class ReviewSummarizeRequest(BaseModel):
    reviews: str


class GreetingRequest(BaseModel):
    productName: str
    relation: Optional[str] = "妈妈"
    occasion: Optional[str] = None


class TTSRequest(BaseModel):
    text: str
    rate: Optional[str] = "normal"


class ImageSearchRequest(BaseModel):
    image: str  # Base64编码的图片


class ChatRequest(BaseModel):
    message: str
    context: Optional[str] = None


class WeeklyReportRequest(BaseModel):
    parentName: Optional[str] = "长辈"
    checkinDays: Optional[int] = 0
    likedProducts: Optional[List[str]] = []
    orderCount: Optional[int] = 0
    totalSpent: Optional[float] = 0
    healthChanges: Optional[str] = None


# ============================================
# 智谱AI调用辅助函数
# ============================================

def _extract_message_text(content: Any) -> str:
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        chunks: List[str] = []
        for item in content:
            if isinstance(item, dict):
                text = item.get("text") or item.get("content")
                if isinstance(text, str) and text.strip():
                    chunks.append(text.strip())
        return "\n".join(chunks).strip()
    return str(content).strip()


def _chat_completion(
    messages: List[Dict[str, Any]],
    model: str,
    temperature: float,
    max_tokens: int
) -> Optional[str]:
    if not AI_ENABLED:
        return None

    headers = {
        "Authorization": f"Bearer {AI_API_KEY}",
        "Content-Type": "application/json"
    }
    payload: Dict[str, Any] = {
        "model": model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens
    }

    with httpx.Client(timeout=45.0) as http_client:
        resp = http_client.post(
            f"{AI_BASE_URL}/chat/completions",
            headers=headers,
            json=payload
        )
        resp.raise_for_status()
        data = resp.json()

    choices = data.get("choices") or []
    if not choices:
        return None
    message = choices[0].get("message") or {}
    return _extract_message_text(message.get("content"))


def call_glm(prompt: str, system_prompt: str = None) -> str:
    """调用大模型文本接口"""
    if not AI_ENABLED:
        return None
    
    try:
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})
        
        return _chat_completion(
            messages=messages,
            model=AI_MODEL,
            temperature=0.7,
            max_tokens=1024
        )
    except Exception as e:
        print(f"AI调用失败: {e}")
        return None


# ============================================
# 健康分析服务
# ============================================

# 健康规则库
HEALTH_RULES = {
    "糖尿病": {
        "keywords": ["含糖", "高糖", "蔗糖", "甜食", "糖分"],
        "warning": "⚠️ 长辈有糖尿病，该商品可能不适合",
        "suggestion": "建议选择无糖或低糖替代品"
    },
    "高血糖": {
        "keywords": ["含糖", "高糖", "蔗糖", "甜食"],
        "warning": "⚠️ 长辈血糖偏高，请注意含糖量",
        "suggestion": "建议选择无糖食品"
    },
    "高血压": {
        "keywords": ["高盐", "咸", "腌制", "酱", "钠"],
        "warning": "⚠️ 长辈有高血压，该商品可能含盐量较高",
        "suggestion": "建议选择低钠食品"
    },
    "高血脂": {
        "keywords": ["高脂", "油炸", "奶油", "肥肉", "胆固醇"],
        "warning": "⚠️ 长辈有高血脂，该商品脂肪或胆固醇含量可能偏高",
        "suggestion": "建议优先选择低脂、清淡类商品"
    },
    "骨质疏松": {
        "keywords": ["高钠", "碳酸", "咖啡因"],
        "warning": "⚠️ 长辈有骨质疏松风险，该商品可能不利于钙质吸收",
        "suggestion": "建议搭配高钙、低钠类商品"
    },
    "痛风": {
        "keywords": ["海鲜", "内脏", "啤酒", "高嘌呤"],
        "warning": "⚠️ 长辈有痛风，该商品可能含嘌呤较高",
        "suggestion": "建议避免高嘌呤食物"
    },
    "高尿酸": {
        "keywords": ["海鲜", "内脏", "啤酒", "高嘌呤"],
        "warning": "⚠️ 长辈尿酸偏高，该商品可能会增加代谢负担",
        "suggestion": "建议避免高嘌呤食物，优先选择清淡饮食"
    },
    "老寒腿": {
        "keywords": ["凉", "冰", "寒"],
        "warning": "⚠️ 长辈有老寒腿，注意保暖",
        "suggestion": "建议选择保暖性好的产品"
    }
}


@app.post("/api/analyze/health")
async def analyze_health(request: HealthAnalyzeRequest):
    """分析商品对长辈的健康风险"""
    warnings = []
    suggestions = []
    ai_analysis = None
    
    product = request.product
    profile = request.profile

    product_text = " ".join(
        filter(
            None,
            [
                product.name or "",
                product.description or "",
                " ".join(product.healthTags or []),
                " ".join(product.warningTags or [])
            ]
        )
    )

    # 规则引擎分析（基础能力）
    if profile.chronicDiseases:
        for disease in profile.chronicDiseases:
            if disease in HEALTH_RULES:
                rule = HEALTH_RULES[disease]
                for keyword in rule["keywords"]:
                    if keyword in product_text:
                        warnings.append(rule["warning"])
                        suggestions.append(rule["suggestion"])
                        break
    
    # 检查过敏源
    if profile.allergies:
        product_text = (product.description or "").lower()
        for allergen in profile.allergies:
            if allergen.lower() in product_text:
                warnings.append(f"⚠️ 长辈对{allergen}过敏，请仔细查看成分")
                suggestions.append(f"确认商品不含{allergen}成分")

    # 使用AI增强分析（如果可用）
    if AI_ENABLED and (profile.chronicDiseases or profile.allergies):
        prompt = f"""作为健康顾问，请分析以下商品对长辈健康的影响：

商品名称：{product.name}
商品描述：{product.description or '无'}
商品标签：{', '.join(product.healthTags or [])}
警告标签：{', '.join(product.warningTags or [])}

长辈健康状况：
- 年龄：{profile.age or '未知'}岁
- 慢性病：{', '.join(profile.chronicDiseases or ['无'])}
- 过敏源：{', '.join(profile.allergies or ['无'])}

请输出 2-3 句话：
1. 先说明这件商品是否适合该长辈
2. 再说明主要风险点或注意事项
3. 最后给出通俗、可执行的购买建议
如果没有明显风险，请明确写出“适合长辈使用”。
"""
        ai_result = call_glm(prompt, "你是一个关心老年人健康的专业顾问，回答要简洁、贴心、易懂。")
        if ai_result:
            ai_analysis = ai_result

    dedup_warnings = list(dict.fromkeys(warnings))
    dedup_suggestions = list(dict.fromkeys(suggestions))
    safe = len(dedup_warnings) == 0
    if ai_analysis and any(keyword in ai_analysis for keyword in ["不适合", "风险较高", "谨慎购买", "避免使用"]):
        safe = False
    
    return {
        "warnings": dedup_warnings,
        "suggestions": dedup_suggestions,
        "aiAnalysis": ai_analysis,
        "safe": safe
    }


# ============================================
# 评论总结服务（AI增强）
# ============================================

@app.post("/api/summarize/reviews")
async def summarize_reviews(request: ReviewSummarizeRequest):
    """总结商品评论"""
    reviews = request.reviews
    
    # 优先使用AI
    if AI_ENABLED:
        prompt = f"""请用一句话总结以下商品评论，要口语化、易懂，适合老年人阅读：

{reviews}

格式示例：买过的人说：xxx"""
        
        ai_result = call_glm(prompt, "你是一个帮助老年人理解商品评论的助手，语言要简洁温暖。")
        if ai_result:
            return {"summary": ai_result, "aiPowered": True}
    
    # 简单的关键词提取（后备方案）
    positive_keywords = ["好", "喜欢", "满意", "推荐", "值得", "舒服", "保暖", "柔软"]
    negative_keywords = ["差", "不好", "失望", "退货", "假货", "不值"]
    
    positive_count = sum(1 for kw in positive_keywords if kw in reviews)
    negative_count = sum(1 for kw in negative_keywords if kw in reviews)
    
    if positive_count > negative_count:
        summary = "买过的人普遍评价不错，质量有保障。"
    elif negative_count > positive_count:
        summary = "部分买家反映存在一些问题，建议谨慎购买。"
    else:
        summary = "评价比较中性，建议根据需求决定。"
    
    return {"summary": summary, "aiPowered": False}


# ============================================
# 情感贺卡生成（AI增强）
# ============================================

GREETING_TEMPLATES = {
    "default": """亲爱的{relation}：

这份{product}是我精心为您挑选的礼物。
希望它能给您带来温暖和快乐。
您的健康和幸福是我最大的心愿。

永远爱您的孩子""",

    "birthday": """亲爱的{relation}：

生日快乐！🎂
这份{product}是我送给您的生日礼物。
愿您身体健康，天天开心！

爱您的孩子""",

    "new_year": """亲爱的{relation}：

新年快乐！🎊
新的一年，愿您身体健康，万事如意。
这份{product}代表我满满的祝福。

爱您的孩子""",

    "mothers_day": """亲爱的{relation}：

母亲节快乐！💐
感谢您多年来的养育之恩。
这份{product}是我对您爱的表达。

永远爱您的孩子"""
}


@app.post("/api/generate/greeting")
async def generate_greeting(request: GreetingRequest):
    """生成情感贺卡"""
    
    # 优先使用AI生成
    if AI_ENABLED:
        occasion_text = f"，场合是{request.occasion}" if request.occasion else ""
        prompt = f"""请为子女写一张送给{request.relation}的温馨贺卡{occasion_text}。

礼物是：{request.productName}

要求：
1. 语言温暖感人，体现孝心
2. 简短精炼，3-4句话即可
3. 适合老年人阅读
4. 可以加入1-2个合适的emoji"""

        ai_result = call_glm(prompt, "你是一个擅长写温馨家庭贺卡的助手。")
        if ai_result:
            return {"greeting": ai_result, "aiPowered": True}
    
    # 模板方案（后备）
    template_key = "default"
    if request.occasion:
        occasion_lower = request.occasion.lower()
        if "生日" in occasion_lower or "birthday" in occasion_lower:
            template_key = "birthday"
        elif "新年" in occasion_lower or "春节" in occasion_lower:
            template_key = "new_year"
        elif "母亲节" in occasion_lower:
            template_key = "mothers_day"
    
    template = GREETING_TEMPLATES[template_key]
    greeting = template.format(
        relation=request.relation or "妈妈",
        product=request.productName
    )
    
    return {"greeting": greeting, "aiPowered": False}


# ============================================
# 智能对话服务（新增）
# ============================================

@app.post("/api/chat")
async def chat(request: ChatRequest):
    """智能对话 - 帮助长辈咨询商品"""
    if not AI_ENABLED:
        return {
            "reply": "抱歉，AI助手暂时不可用。请稍后再试。",
            "aiPowered": False
        }
    
    system_prompt = """你是"连心选"的AI购物助手，专门帮助老年人选购商品。你的特点：
1. 语言简洁、亲切、易懂
2. 像晚辈一样关心长辈的健康
3. 会主动询问健康情况来推荐合适的商品
4. 不推荐不适合老年人的商品"""
    
    prompt = request.message
    if request.context:
        prompt = f"上下文：{request.context}\n\n用户问：{request.message}"
    
    ai_result = call_glm(prompt, system_prompt)
    
    return {
        "reply": ai_result or "抱歉，我没有理解您的问题。请再说一遍？",
        "aiPowered": True
    }


# ============================================
# 商品推荐解释（新增）
# ============================================

@app.post("/api/explain/recommendation")
async def explain_recommendation(request: HealthAnalyzeRequest):
    """解释为什么推荐这个商品给长辈"""
    if not AI_ENABLED:
        return {"explanation": f"这款{request.product.name}很适合长辈使用。"}
    
    prompt = f"""请用2-3句话解释为什么推荐这款商品给长辈：

商品：{request.product.name}
特点：{request.product.description or '无'}
健康标签：{', '.join(request.product.healthTags or [])}

长辈情况：
- 年龄：{request.profile.age or '未知'}岁
- 爱好：{', '.join(request.profile.hobbies or ['未知'])}

用温暖的语气，像子女推荐给父母一样。"""

    ai_result = call_glm(prompt, "你是一个贴心的子女，在向父母推荐商品。")
    
    return {
        "explanation": ai_result or f"这款{request.product.name}很适合长辈使用。",
        "aiPowered": True
    }


# ============================================
# 语音合成服务 (TTS)
# ============================================

def _generate_fallback_wav(text: str, rate: str = "normal") -> bytes:
    """
    Generate a short WAV tone as a fallback so clients always receive playable audio.
    """
    sample_rate = 16000
    seconds_per_char = 0.09 if rate == "normal" else 0.12
    duration = min(max(1.2, len(text) * seconds_per_char), 8.0)
    base_freq = 430.0 if rate == "normal" else 360.0
    amplitude = 9000
    total_samples = int(sample_rate * duration)

    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)

        for i in range(total_samples):
            t = i / sample_rate
            freq = base_freq + 30 * math.sin(2 * math.pi * 0.45 * t)
            value = int(amplitude * math.sin(2 * math.pi * freq * t))
            wav_file.writeframesraw(struct.pack("<h", value))

    return buffer.getvalue()


def _call_remote_tts(text: str, rate: str = "normal") -> Optional[Tuple[bytes, str]]:
    if not TTS_API_URL:
        return None

    headers = {"Content-Type": "application/json"}
    if TTS_API_KEY:
        headers["Authorization"] = f"Bearer {TTS_API_KEY}"

    payload = {
        "text": text,
        "rate": rate
    }

    with httpx.Client(timeout=45.0) as client:
        resp = client.post(TTS_API_URL, headers=headers, json=payload)
        resp.raise_for_status()
        media_type = (resp.headers.get("content-type") or "").split(";")[0].strip().lower()

        if media_type == "application/json":
            body = resp.json()
            audio_base64 = body.get("audio") if isinstance(body, dict) else None
            if not audio_base64 and isinstance(body, dict):
                audio_base64 = (body.get("data") or {}).get("audio")
            if audio_base64:
                return base64.b64decode(audio_base64), "audio/wav"
            return None

        if not media_type:
            media_type = "audio/wav"
        return resp.content, media_type


@app.post("/api/tts")
async def text_to_speech(request: TTSRequest):
    text = (request.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text cannot be empty")

    rate = request.rate or "normal"

    try:
        remote = _call_remote_tts(text, rate)
        if remote:
            audio_bytes, media_type = remote
            return Response(content=audio_bytes, media_type=media_type)
    except Exception as e:
        print(f"TTS remote call failed: {e}")
        raise HTTPException(status_code=502, detail="tts provider request failed")

    # No provider configured or provider returned no playable audio.
    raise HTTPException(status_code=503, detail="tts provider not configured")


@app.post("/api/tryon/tasks")
async def create_tryon_task(
    person_image: Optional[UploadFile] = File(None),
    person_image_url: str = Form(""),
    garment_role: str = Form("top"),
    garment_image_url: str = Form(""),
    top_garment_url: str = Form(""),
    bottom_garment_url: str = Form(""),
    preserve_original_other: bool = Form(False),
    restore_face: bool = Form(True),
    resolution: int = Form(-1),
):
    request_id = uuid.uuid4().hex
    role = _normalize_tryon_role(garment_role)

    try:
        if person_image is not None:
            person_url = await _save_upload_as_tryon_source(person_image, request_id, "person")
        else:
            person_url = await _materialize_tryon_image(person_image_url, request_id, "person")

        if not person_url:
            raise HTTPException(status_code=400, detail="缺少模特图，请上传照片或提供公网图片地址")

        top_url = str(top_garment_url or "").strip()
        bottom_url = str(bottom_garment_url or "").strip()
        primary_url = str(garment_image_url or "").strip()

        if not top_url and not bottom_url and primary_url:
            prepared_primary = await _materialize_tryon_image(primary_url, request_id, "primary")
            if role in {"top", "dress"}:
                top_url = prepared_primary
            else:
                bottom_url = prepared_primary

        if top_url and not top_url.startswith("http"):
            top_url = await _materialize_tryon_image(top_url, request_id, "top")
        if bottom_url and not bottom_url.startswith("http"):
            bottom_url = await _materialize_tryon_image(bottom_url, request_id, "bottom")

        if preserve_original_other:
            if role == "top" and top_url and not bottom_url:
                bottom_url = await _extract_person_garment(person_url, "lower")
            elif role == "bottom" and bottom_url and not top_url:
                top_url = await _extract_person_garment(person_url, "upper")

        payload = _build_tryon_payload(
            person_image_url=person_url,
            top_garment_url=top_url,
            bottom_garment_url=bottom_url,
            resolution=resolution,
            restore_face=restore_face,
        )
        data = await _dashscope_post_json(
            "/services/aigc/image2image/image-synthesis",
            payload,
            async_mode=True,
            oss_resolve=any(str(url or "").startswith("oss://") for url in [person_url, top_url, bottom_url]),
        )
        output = data.get("output") or {}
        return {
            "success": True,
            "requestId": data.get("request_id"),
            "taskId": output.get("task_id"),
            "taskStatus": output.get("task_status") or "PENDING",
            "garmentRole": role,
            "restoreFace": bool(restore_face),
            "resolution": resolution if resolution in {-1, 1024, 1280} else -1,
            "preserveOriginalOther": bool(preserve_original_other),
        }
    finally:
        if person_image is not None:
            try:
                await person_image.close()
            except Exception:
                pass


@app.get("/api/tryon/tasks/{task_id}")
async def get_tryon_task(task_id: str, request: Request):
    task_id = str(task_id or "").strip()
    if not task_id:
        raise HTTPException(status_code=400, detail="缺少 task_id")

    data = await _dashscope_get_json(f"/tasks/{task_id}")
    output = data.get("output") or {}
    task_status = output.get("task_status") or "UNKNOWN"
    response: Dict[str, Any] = {
        "success": task_status == "SUCCEEDED",
        "requestId": data.get("request_id"),
        "taskId": output.get("task_id") or task_id,
        "taskStatus": task_status,
        "submitTime": output.get("submit_time"),
        "scheduledTime": output.get("scheduled_time"),
        "endTime": output.get("end_time"),
        "usage": data.get("usage") or {},
    }

    image_url = str(output.get("image_url") or "").strip()
    if task_status == "SUCCEEDED" and image_url:
        cached_path = await _cache_tryon_result_image(task_id, image_url)
        relative_path = f"/generated/tryon/{task_id}/{cached_path.name}"
        response["resultImageUrl"] = _build_client_asset_url(request, relative_path)
        response["dashscopeImageUrl"] = image_url
        response["imageCount"] = ((data.get("usage") or {}).get("image_count") or 1)
        return response

    if task_status == "FAILED":
        response["errorCode"] = output.get("code")
        response["message"] = _format_tryon_error(output.get("code"), output.get("message"))
        return response

    response["message"] = output.get("message") or "任务处理中"
    return response

# ============================================
# 以图搜图服务
# ============================================

@app.post("/api/search/image")
async def search_by_image(request: ImageSearchRequest):
    """以图搜物 - 识别图片中的商品"""
    if AI_ENABLED:
        try:
            # 使用兼容视觉输入的大模型接口
            keywords_text = _chat_completion(
                messages=[{
                    "role": "user",
                    "content": [
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": request.image if request.image.startswith("http") else f"data:image/jpeg;base64,{request.image}"
                            }
                        },
                        {
                            "type": "text",
                            "text": "请识别图片中的商品，返回2-3个搜索关键词，用逗号分隔。只返回关键词，不要其他内容。"
                        }
                    ]
                }],
                model=AI_VISION_MODEL,
                temperature=0.5,
                max_tokens=100
            )
            if not keywords_text:
                raise ValueError("empty image recognition result")
            keywords_text = keywords_text.strip()
            keywords = [k.strip() for k in keywords_text.replace("，", ",").split(",")]
            return {
                "keywords": keywords,
                "description": keywords_text,
                "aiPowered": True
            }
        except Exception as e:
            print(f"图片识别失败: {e}")

    return {
        "keywords": [],
        "description": "图片识别功能需要配置AI服务",
        "aiPowered": False
    }


# ============================================
# 平台推荐服务（抖音/B站热门商品）
# ============================================

class PlatformRecommendRequest(BaseModel):
    categoryId: Optional[int] = None
    platform: Optional[str] = None  # DOUYIN / BILIBILI / ALL
    profileAge: Optional[int] = None
    chronicDiseases: Optional[List[str]] = []


# 模拟平台热门商品数据
PLATFORM_PRODUCTS = {
    "DOUYIN": [
        {
            "id": 101,
            "name": "【抖音爆款】智能血压计 语音播报",
            "price": 199,
            "categoryId": 2,
            "platformSource": "DOUYIN",
            "platformUrl": "https://v.douyin.com/example",
            "platformStats": {"views": "128.5万", "likes": "3.2万", "upName": "健康养生号"},
            "healthTags": ["语音播报", "大屏显示", "一键测量"],
            "aiRecommendReason": "抖音128万播放，适合老年人的语音播报功能很贴心"
        },
        {
            "id": 102,
            "name": "【抖音热销】无糖低GI燕麦片",
            "price": 68,
            "categoryId": 1,
            "platformSource": "DOUYIN",
            "platformUrl": "https://v.douyin.com/example2",
            "platformStats": {"views": "89.3万", "likes": "2.1万", "upName": "营养师小王"},
            "healthTags": ["无糖", "低GI", "高纤维"],
            "aiRecommendReason": "营养师推荐，糖尿病患者也能放心吃"
        },
        {
            "id": 103,
            "name": "【抖音同款】加厚羊绒护膝",
            "price": 89,
            "categoryId": 3,
            "platformSource": "DOUYIN",
            "platformUrl": "https://v.douyin.com/example3",
            "platformStats": {"views": "56.7万", "likes": "1.8万", "upName": "银发关爱"},
            "healthTags": ["保暖", "透气", "不勒腿"],
            "aiRecommendReason": "老寒腿必备，56万人验证的保暖效果"
        }
    ],
    "BILIBILI": [
        {
            "id": 201,
            "name": "【UP主推荐】老年智能手机 超大字体",
            "price": 899,
            "categoryId": 5,
            "platformSource": "BILIBILI",
            "platformUrl": "https://www.bilibili.com/video/example",
            "platformStats": {"views": "45.2万", "likes": "1.5万", "upName": "科技银发派"},
            "healthTags": ["超大字体", "一键急救", "远程协助"],
            "aiRecommendReason": "B站科技UP主实测，适老化做得很好"
        },
        {
            "id": 202,
            "name": "【B站测评】德国进口足浴盆",
            "price": 599,
            "categoryId": 4,
            "platformSource": "BILIBILI",
            "platformUrl": "https://www.bilibili.com/video/example2",
            "platformStats": {"views": "32.1万", "likes": "0.9万", "upName": "养生达人"},
            "healthTags": ["恒温加热", "气泡按摩", "红外理疗"],
            "aiRecommendReason": "B站养生区推荐，促进血液循环"
        },
        {
            "id": 203,
            "name": "【测评】便携式制氧机",
            "price": 2980,
            "categoryId": 2,
            "platformSource": "BILIBILI",
            "platformUrl": "https://www.bilibili.com/video/example3",
            "platformStats": {"views": "28.5万", "likes": "0.8万", "upName": "医疗科普"},
            "healthTags": ["医用级", "静音设计", "便携"],
            "aiRecommendReason": "医学UP主专业测评，适合有呼吸问题的老人"
        }
    ]
}


@app.post("/api/recommend/platform")
async def platform_recommend(request: PlatformRecommendRequest):
    """平台热门商品推荐（抖音/B站）"""
    results = []
    
    # 获取对应平台的商品
    if request.platform == "DOUYIN":
        results = PLATFORM_PRODUCTS["DOUYIN"].copy()
    elif request.platform == "BILIBILI":
        results = PLATFORM_PRODUCTS["BILIBILI"].copy()
    else:
        # 全部平台
        results = PLATFORM_PRODUCTS["DOUYIN"].copy() + PLATFORM_PRODUCTS["BILIBILI"].copy()
    
    # 按分类筛选
    if request.categoryId:
        results = [p for p in results if p.get("categoryId") == request.categoryId]
    
    # AI增强推荐理由
    if AI_ENABLED and results and request.chronicDiseases:
        for product in results[:3]:  # 只对前3个做AI分析
            prompt = f"""请用一句话说明为什么推荐这个商品给有{', '.join(request.chronicDiseases)}的老年人：
商品：{product['name']}
特点：{', '.join(product.get('healthTags', []))}
平台热度：{product.get('platformStats', {}).get('views', '未知')}播放"""
            
            ai_result = call_glm(prompt, "你是一个关心老人健康的子女，语言要温暖简洁。")
            if ai_result:
                product["aiRecommendReason"] = ai_result
    
    return {
        "products": results,
        "platform": request.platform or "ALL",
        "total": len(results)
    }


@app.get("/api/category/list")
async def get_categories():
    """获取商品分类列表"""
    return {
        "categories": [
            {"id": 1, "name": "保健食品", "icon": "🍵", "description": "燕麦、营养品、保健品"},
            {"id": 2, "name": "智能穿戴", "icon": "⌚", "description": "智能手表、血压计、血糖仪"},
            {"id": 3, "name": "服饰保暖", "icon": "🧥", "description": "保暖内衣、羽绒服、护膝"},
            {"id": 4, "name": "家居养生", "icon": "🏠", "description": "足浴盆、按摩器、养生壶"},
            {"id": 5, "name": "数码适老", "icon": "📱", "description": "老年手机、大字体平板"},
            {"id": 6, "name": "户外出行", "icon": "🚶", "description": "拐杖、轮椅、老年车"}
        ]
    }


# ============================================
# 健康周报生成
# ============================================

@app.post("/api/report/weekly")
async def generate_weekly_report(request: WeeklyReportRequest):
    """生成健康周报"""
    if AI_ENABLED:
        prompt = f"""请为{request.parentName}生成一份温馨的本周生活周报：

本周数据：
- 签到天数：{request.checkinDays}天
- 喜欢的商品：{', '.join(request.likedProducts) if request.likedProducts else '暂无'}
- 下单数量：{request.orderCount}单
- 消费金额：¥{request.totalSpent}
- 健康变化：{request.healthChanges or '无特殊变化'}

要求：
1. 语言温馨、像子女关心父母的语气
2. 总结本周活跃度
3. 给出健康小建议
4. 3-5句话即可
5. 可以加入合适的emoji"""

        ai_result = call_glm(prompt, "你是一个关心老人的AI助手，语言要温暖、简洁、易懂。")
        if ai_result:
            return {"report": ai_result, "aiPowered": True}

    # 后备方案
    report = f"本周{request.parentName}签到了{request.checkinDays}天"
    if request.likedProducts:
        report += f"，喜欢了{len(request.likedProducts)}个商品"
    if request.orderCount > 0:
        report += f"，下了{request.orderCount}单"
    report += "。继续保持健康的生活习惯哦！"

    return {"report": report, "aiPowered": False}


# ============================================
# 健康检查
# ============================================

@app.get("/health")
async def health_check():
    return {
        "status": "ok",
        "service": "HeartLink AI Service",
        "aiEnabled": AI_ENABLED,
        "model": AI_MODEL if AI_ENABLED else None,
        "tryOnEnabled": bool(DASHSCOPE_API_KEY),
        "tryOnModel": TRYON_MODEL if DASHSCOPE_API_KEY else None
    }


@app.get("/")
async def root():
    return {
        "service": "HeartLink AI Service",
        "version": "3.0.0",
        "aiEnabled": AI_ENABLED,
        "endpoints": [
            "POST /api/analyze/health - 健康风险分析（AI增强）",
            "POST /api/summarize/reviews - 评论总结（AI增强）",
            "POST /api/generate/greeting - 生成贺卡（AI增强）",
            "POST /api/chat - 智能对话（新增）",
            "POST /api/explain/recommendation - 推荐解释（新增）",
            "POST /api/tts - 语音合成",
            "POST /api/search/image - 以图搜图",
            "POST /api/report/weekly - 健康周报生成（新增）"
        ]
    }


if __name__ == "__main__":
    import sys
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    
    print("===========================================")
    print("   HeartLink AI Service v3.0")
    print(f"   AI Enabled: {AI_ENABLED}")
    if AI_ENABLED:
        print(f"   Model Connected: {AI_MODEL}")
        print(f"   Base URL: {AI_BASE_URL}")
    else:
        print("   AI not enabled, using rule engine mode")
    print("   API: http://localhost:8090")
    print("===========================================")
    uvicorn.run(app, host="0.0.0.0", port=8090)
