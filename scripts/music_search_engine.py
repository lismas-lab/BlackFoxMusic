"""
Black Fox Music - InnerTube Search & Catalog Engine
Полноценный парсер каталога YouTube Music:
- Поиск треков (песен)
- Поиск альбомов и просмотр треклиста альбома
- Поиск артистов и их дискографии
- Получение обложек в высоком разрешении (до 1080x1080)
"""

import json
import re
import urllib.request
import urllib.parse
from typing import List, Dict, Optional, Any

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
INNERTUBE_URL = "https://music.youtube.com/youtubei/v1"
HEADERS = {
    "User-Agent": USER_AGENT,
    "Content-Type": "application/json",
    "Referer": "https://music.youtube.com/",
    "Origin": "https://music.youtube.com"
}
BASE_CONTEXT = {
    "client": {
        "clientName": "WEB_REMIX",
        "clientVersion": "1.20240101.01.00",
        "hl": "ru",
        "gl": "US"
    }
}

def clean_thumbnail(url: str, size: int = 1080) -> str:
    """Заменяет параметры размера обложки на максимальное качество (до 1080x1080)"""
    if not url:
        return ""
    if "=w" in url and "-h" in url:
        return re.sub(r"=w\d+-h\d+[^?]*", f"=w{size}-h{size}-l90-rj", url)
    return url

def send_innertube_request(endpoint: str, payload: dict) -> dict:
    url = f"{INNERTUBE_URL}/{endpoint}?prettyPrint=false"
    body = {"context": BASE_CONTEXT}
    body.update(payload)
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers=HEADERS
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))

def search_tracks(query: str, limit: int = 20) -> List[Dict[str, Any]]:
    """Поиск песен (треков)"""
    data = send_innertube_request("search", {"query": query, "params": "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"})
    tracks = []
    
    sections = data.get("contents", {}).get("tabbedSearchResultsRenderer", {}).get("tabs", [{}])[0] \
                   .get("tabRenderer", {}).get("content", {}).get("sectionListRenderer", {}).get("contents", [])
    
    for section in sections:
        items = section.get("musicShelfRenderer", {}).get("contents", [])
        if not items:
            items = section.get("itemSectionRenderer", {}).get("contents", [])
        
        for item in items:
            mir = item.get("musicResponsiveListItemRenderer")
            if not mir:
                continue
            
            cols = mir.get("flexColumns", [])
            if not cols:
                continue
            
            title = cols[0]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0]["text"]
            
            details_runs = cols[1]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"] if len(cols) > 1 else []
            artist = "Unknown"
            album = None
            duration = ""
            
            runs_texts = [r["text"] for r in details_runs if r["text"] != " • "]
            if len(runs_texts) >= 1:
                artist = runs_texts[0]
            if len(runs_texts) >= 2:
                if ":" in runs_texts[-1]:
                    duration = runs_texts[-1]
                    if len(runs_texts) >= 3:
                        album = runs_texts[1]
                else:
                    album = runs_texts[1]

            play_nav = mir.get("overlay", {}).get("musicItemThumbnailOverlayRenderer", {}).get("content", {}) \
                          .get("musicPlayButtonRenderer", {}).get("playNavigationEndpoint", {})
            video_id = play_nav.get("watchEndpoint", {}).get("videoId")
            
            thumbs = mir.get("thumbnail", {}).get("musicThumbnailRenderer", {}).get("thumbnail", {}).get("thumbnails", [])
            raw_thumb = thumbs[-1]["url"] if thumbs else ""
            hq_thumb = clean_thumbnail(raw_thumb, size=1080)
            
            if video_id:
                tracks.append({
                    "id": video_id,
                    "title": title,
                    "artist": artist,
                    "album": album,
                    "duration": duration,
                    "thumbnail": hq_thumb
                })
                if len(tracks) >= limit:
                    break
        if len(tracks) >= limit:
            break
            
    return tracks

def search_albums(query: str, limit: int = 15) -> List[Dict[str, Any]]:
    """Поиск альбомов с обложками и типом (Альбом/Сингл/EP)"""
    data = send_innertube_request("search", {"query": query, "params": "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"})
    albums = []
    
    sections = data.get("contents", {}).get("tabbedSearchResultsRenderer", {}).get("tabs", [{}])[0] \
                   .get("tabRenderer", {}).get("content", {}).get("sectionListRenderer", {}).get("contents", [])
                   
    for section in sections:
        items = section.get("musicShelfRenderer", {}).get("contents", [])
        if not items:
            items = section.get("itemSectionRenderer", {}).get("contents", [])
            
        for item in items:
            mir = item.get("musicResponsiveListItemRenderer")
            if not mir:
                continue
            cols = mir.get("flexColumns", [])
            if not cols:
                continue
            title = cols[0]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0]["text"]
            
            # Извлекаем browseId
            browse_id = mir.get("navigationEndpoint", {}).get("browseEndpoint", {}).get("browseId")
            if not browse_id:
                nav = cols[0]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0].get("navigationEndpoint", {})
                browse_id = nav.get("browseEndpoint", {}).get("browseId")
            
            details_runs = cols[1]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"] if len(cols) > 1 else []
            artist = "Unknown"
            year = ""
            album_type = "Альбом"
            
            for r in details_runs:
                t = r.get("text", "")
                if t in ["Альбом", "Сингл", "EP", "Album", "Single"]:
                    album_type = t
                elif re.match(r"^\d{4}$", t):
                    year = t
                elif t not in [" • ", ""]:
                    artist = t
                    
            thumbs = mir.get("thumbnail", {}).get("musicThumbnailRenderer", {}).get("thumbnail", {}).get("thumbnails", [])
            raw_thumb = thumbs[-1]["url"] if thumbs else ""
            
            if browse_id:
                albums.append({
                    "browseId": browse_id,
                    "title": title,
                    "artist": artist,
                    "type": album_type,
                    "year": year,
                    "cover": clean_thumbnail(raw_thumb, size=1080)
                })
                if len(albums) >= limit:
                    break
        if len(albums) >= limit:
            break
            
    return albums

def search_artists(query: str, limit: int = 10) -> List[Dict[str, Any]]:
    """Поиск исполнителей (артистов) с аватарами"""
    data = send_innertube_request("search", {"query": query, "params": "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"})
    artists = []
    
    sections = data.get("contents", {}).get("tabbedSearchResultsRenderer", {}).get("tabs", [{}])[0] \
                   .get("tabRenderer", {}).get("content", {}).get("sectionListRenderer", {}).get("contents", [])
                   
    for section in sections:
        items = section.get("musicShelfRenderer", {}).get("contents", [])
        if not items:
            items = section.get("itemSectionRenderer", {}).get("contents", [])
            
        for item in items:
            mir = item.get("musicResponsiveListItemRenderer")
            if not mir:
                continue
            cols = mir.get("flexColumns", [])
            if not cols:
                continue
            name = cols[0]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0]["text"]
            
            browse_id = mir.get("navigationEndpoint", {}).get("browseEndpoint", {}).get("browseId")
            details = cols[1]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0]["text"] if len(cols) > 1 else ""
            
            thumbs = mir.get("thumbnail", {}).get("musicThumbnailRenderer", {}).get("thumbnail", {}).get("thumbnails", [])
            avatar = clean_thumbnail(thumbs[-1]["url"], size=544) if thumbs else ""
            
            if browse_id:
                artists.append({
                    "channelId": browse_id,
                    "name": name,
                    "subscribers": details,
                    "avatar": avatar
                })
                if len(artists) >= limit:
                    break
        if len(artists) >= limit:
            break
            
    return artists

def get_album_details(browse_id: str) -> Dict[str, Any]:
    """Получение заголовка альбома и полного треклиста по browseId"""
    data = send_innertube_request("browse", {"browseId": browse_id})
    
    # 1. Извлекаем заголовок альбома
    album_title = "Unknown Album"
    artist = "Unknown Artist"
    year = ""
    cover = ""
    
    # Проверяем twoColumn или обычный
    tc = data.get("contents", {}).get("twoColumnBrowseResultsRenderer")
    if tc:
        tabs = tc.get("tabs", [{}])
        first_tab_contents = tabs[0].get("tabRenderer", {}).get("content", {}).get("sectionListRenderer", {}).get("contents", [])
        if first_tab_contents:
            header = first_tab_contents[0].get("musicResponsiveHeaderRenderer", {})
            album_title = header.get("title", {}).get("runs", [{}])[0].get("text", album_title)
            subtitle_runs = header.get("subtitle", {}).get("runs", [])
            for r in subtitle_runs:
                txt = r.get("text", "")
                if re.match(r"^\d{4}$", txt):
                    year = txt
                elif txt not in [" • ", "Альбом", "Сингл", "EP", "Album", "Single"]:
                    artist = txt
            thumbs = header.get("thumbnail", {}).get("musicThumbnailRenderer", {}).get("thumbnail", {}).get("thumbnails", [])
            if thumbs:
                cover = clean_thumbnail(thumbs[-1]["url"], size=1080)
        
        # Треклист находится в secondaryContents
        sec_contents = tc.get("secondaryContents", {}).get("sectionListRenderer", {}).get("contents", [])
        tracks_raw = []
        for s in sec_contents:
            shelf = s.get("musicShelfRenderer")
            if shelf:
                tracks_raw = shelf.get("contents", [])
                break
    else:
        # Fallback для одноколоночного макета
        header = data.get("header", {}).get("musicDetailHeaderRenderer", {})
        album_title = header.get("title", {}).get("runs", [{}])[0].get("text", album_title)
        thumbs = header.get("thumbnail", {}).get("croppedSquareThumbnailRenderer", {}).get("thumbnail", {}).get("thumbnails", [])
        if thumbs:
            cover = clean_thumbnail(thumbs[-1]["url"], size=1080)
        tracks_raw = data.get("contents", {}).get("singleColumnBrowseResultsRenderer", {}) \
                         .get("tabs", [{}])[0].get("tabRenderer", {}).get("content", {}) \
                         .get("sectionListRenderer", {}).get("contents", [{}])[0] \
                         .get("musicShelfRenderer", {}).get("contents", [])
    
    tracks = []
    for index, item in enumerate(tracks_raw, start=1):
        mir = item.get("musicResponsiveListItemRenderer")
        if not mir:
            continue
        cols = mir.get("flexColumns", [])
        track_title = cols[0]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0]["text"]
        
        fixed_cols = mir.get("fixedColumns", [])
        duration = ""
        if fixed_cols:
            duration = fixed_cols[0]["musicResponsiveListItemFixedColumnRenderer"]["text"]["runs"][0]["text"]
            
        play_nav = mir.get("overlay", {}).get("musicItemThumbnailOverlayRenderer", {}).get("content", {}) \
                      .get("musicPlayButtonRenderer", {}).get("playNavigationEndpoint", {})
        video_id = play_nav.get("watchEndpoint", {}).get("videoId")
        
        tracks.append({
            "trackNumber": index,
            "id": video_id,
            "title": track_title,
            "artist": artist,
            "duration": duration
        })
        
    return {
        "browseId": browse_id,
        "title": album_title,
        "artist": artist,
        "year": year,
        "cover": cover,
        "trackCount": len(tracks),
        "tracks": tracks
    }

if __name__ == "__main__":
    print("==================================================")
    print("   BLACK FOX MUSIC - INNER TUBE CATALOG TEST      ")
    print("==================================================")
    
    print("\n[1] Поиск альбомов по запросу: 'Meteora'")
    albums = search_albums("Meteora", limit=2)
    for a in albums:
        print(f" -> [{a['type']}] {a['title']} ({a['year']}) by {a['artist']} [ID: {a['browseId']}]")
        print(f"    Обложка HQ: {a['cover'][:75]}...")
    
    if albums:
        album_id = albums[0]["browseId"]
        print(f"\n[2] Загрузка треклиста альбома '{albums[0]['title']}' (ID: {album_id}):")
        details = get_album_details(album_id)
        print(f"    Альбом: {details['title']} ({details['year']}) | Артист: {details['artist']} | Треков: {details['trackCount']}")
        for t in details["tracks"][:6]:
            print(f"    #{t['trackNumber']:02d} {t['title']} ({t['duration']}) [ID: {t['id']}]")
            
    print("\n[3] Поиск артистов по запросу: 'The Weeknd'")
    artists = search_artists("The Weeknd", limit=2)
    for art in artists:
        print(f" -> Артист: {art['name']} [ID: {art['channelId']}]")
        print(f"    Аватар: {art['avatar'][:75]}...")
