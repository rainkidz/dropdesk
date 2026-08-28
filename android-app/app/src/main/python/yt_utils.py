"""
YouTube extraction using yt-dlp.
Called from Kotlin via Chaquopy Python bridge.
"""
import json
import os
import sys
import yt_dlp

# Module-level progress state — polled by Kotlin via get_progress()
_progress = {"phase": "idle", "percent": 0.0, "speed": "", "eta": "", "downloaded": 0, "total": 0, "filename": "", "error": ""}


def get_video_info(url):
    """
    Extract video info using yt-dlp.
    Returns JSON string with video details and available formats.
    """
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'no_check_certificates': True,
        'geo_bypass': True,
        'skip_download': True,
        'extract_flat': False,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            if info is None:
                return json.dumps({"error": "No info found"})

            # Build clean format list
            formats = []
            for fmt in info.get('formats', []):
                formats.append({
                    'format_id': fmt.get('format_id', ''),
                    'ext': fmt.get('ext', ''),
                    'resolution': fmt.get('resolution', ''),
                    'height': fmt.get('height', 0) or 0,
                    'width': fmt.get('width', 0) or 0,
                    'fps': fmt.get('fps', 0) or 0,
                    'vcodec': fmt.get('vcodec', 'none'),
                    'acodec': fmt.get('acodec', 'none'),
                    'filesize': fmt.get('filesize', 0) or fmt.get('filesize_approx', 0) or 0,
                    'tbr': fmt.get('tbr', 0) or 0,
                    'abr': fmt.get('abr', 0) or 0,
                    'format_note': fmt.get('format_note', ''),
                    'url': fmt.get('url', ''),
                })

            result = {
                'title': info.get('title', 'Unknown'),
                'duration': info.get('duration', 0) or 0,
                'thumbnail': info.get('thumbnail', ''),
                'uploader': info.get('uploader', ''),
                'formats': formats,
            }

            return json.dumps(result)

    except Exception as e:
        return json.dumps({"error": str(e)})


def get_progress():
    """Return current download progress as JSON string. Called by Kotlin polling."""
    return json.dumps(_progress)


def download_video(url, output_path, format_str, cookies_file="", progress_callback=None):
    """
    Download video using yt-dlp.
    format_str: "bestvideo+bestaudio" or "bestaudio" etc.
    output_path: path template like "/path/to/%(title)s.%(ext)s"
    cookies_file: path to Netscape cookies.txt file for authentication
    """
    global _progress
    _progress = {"phase": "extracting", "percent": 0.0, "speed": "", "eta": "", "downloaded": 0, "total": 0, "filename": "", "error": ""}
    # Clear any stale state
    _progress['_done'] = False
    _progress['_error'] = False

    def progress_hook(d):
        global _progress
        if d['status'] == 'downloading':
            _progress['phase'] = 'downloading'
            try:
                _progress['percent'] = float(d.get('_percent_str', '0%').strip().replace('%', '').strip())
            except (ValueError, AttributeError):
                _progress['percent'] = 0.0
            _progress['speed'] = d.get('_speed_str', '').strip()
            _progress['eta'] = d.get('_eta_str', '').strip()
            _progress['downloaded'] = d.get('_downloaded_bytes', 0) or 0
            _progress['total'] = d.get('_total_bytes', 0) or d.get('_total_bytes_estimate', 0) or 0
            _progress['filename'] = d.get('filename', '')
        elif d['status'] == 'finished':
            # Only mark as finalizing on first finish — don't reset to downloading
            if _progress['phase'] != 'done':
                _progress['phase'] = 'finalizing'
            _progress['percent'] = 100.0
            _progress['filename'] = d.get('filename', '')

    ydl_opts = {
        'format': format_str,
        'outtmpl': output_path,
        'quiet': True,
        'no_warnings': True,
        'no_check_certificates': True,
        'geo_bypass': True,
        'progress_hooks': [progress_hook],
        'noplaylist': True,
        'postprocessors': [],
    }

    # Use cookies for authenticated downloads (Facebook, Instagram, etc.)
    if cookies_file and os.path.isfile(cookies_file):
        ydl_opts['cookiefile'] = cookies_file

    try:
        _progress['phase'] = 'downloading'
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        _progress['phase'] = 'done'
        _progress['percent'] = 100.0
        _progress['_done'] = True
        return json.dumps({"success": True})
    except Exception as e:
        _progress['phase'] = 'error'
        _progress['error'] = str(e)
        _progress['_error'] = True
        return json.dumps({"error": str(e)})


def download_video_audio(url, output_path, video_format, audio_format="bestaudio", cookies_file="", ffmpeg_location=""):
    """
    Download video + audio and merge using ffmpeg.
    video_format: e.g. "bestvideo[height<=1080][ext=mp4]"
    audio_format: e.g. "bestaudio[ext=m4a]"
    This is for premium users who want 1080p+ with audio.
    """
    global _progress
    _progress = {"phase": "extracting", "percent": 0.0, "speed": "", "eta": "", "downloaded": 0, "total": 0, "filename": "", "error": ""}
    _progress['_done'] = False
    _progress['_error'] = False

    def progress_hook(d):
        global _progress
        if d['status'] == 'downloading':
            _progress['phase'] = 'downloading'
            try:
                _progress['percent'] = float(d.get('_percent_str', '0%').strip().replace('%', '').strip())
            except (ValueError, AttributeError):
                _progress['percent'] = 0.0
            _progress['speed'] = d.get('_speed_str', '').strip()
            _progress['eta'] = d.get('_eta_str', '').strip()
            _progress['downloaded'] = d.get('_downloaded_bytes', 0) or 0
            _progress['total'] = d.get('_total_bytes', 0) or d.get('_total_bytes_estimate', 0) or 0
            _progress['filename'] = d.get('filename', '')
        elif d['status'] == 'finished':
            if _progress['phase'] != 'done':
                _progress['phase'] = 'merging'  # Merging video + audio
            _progress['percent'] = 100.0
            _progress['filename'] = d.get('filename', '')

    # Merge format: bestvideo + bestaudio → single file
    merge_format = f"{video_format}+{audio_format}"

    ydl_opts = {
        'format': merge_format,
        'outtmpl': output_path,
        'quiet': True,
        'no_warnings': True,
        'no_check_certificates': True,
        'geo_bypass': True,
        'progress_hooks': [progress_hook],
        'noplaylist': True,
        'merge_output_format': 'mp4',
        'postprocessors': [],
    }

    # Set ffmpeg location if available
    if ffmpeg_location and os.path.isdir(ffmpeg_location):
        ydl_opts['ffmpeg_location'] = ffmpeg_location

    # Use cookies for authenticated downloads
    if cookies_file and os.path.isfile(cookies_file):
        ydl_opts['cookiefile'] = cookies_file

    try:
        _progress['phase'] = 'downloading'
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        _progress['phase'] = 'done'
        _progress['percent'] = 100.0
        _progress['_done'] = True
        return json.dumps({"success": True})
    except Exception as e:
        _progress['phase'] = 'error'
        _progress['error'] = str(e)
        _progress['_error'] = True
        return json.dumps({"error": str(e)})


def download_playlist(url, output_path, format_str, max_videos=50, cookies_file="", ffmpeg_location=""):
    """
    Download entire playlist.
    format_str: "bestvideo+bestaudio" or "bestaudio" etc.
    max_videos: limit number of videos to download
    """
    global _progress
    _progress = {"phase": "extracting", "percent": 0.0, "speed": "", "eta": "", "downloaded": 0, "total": 0, "filename": "", "error": "", "playlist_count": 0, "playlist_index": 0}
    _progress['_done'] = False
    _progress['_error'] = False

    def progress_hook(d):
        global _progress
        if d['status'] == 'downloading':
            _progress['phase'] = 'downloading'
            try:
                _progress['percent'] = float(d.get('_percent_str', '0%').strip().replace('%', '').strip())
            except (ValueError, AttributeError):
                _progress['percent'] = 0.0
            _progress['speed'] = d.get('_speed_str', '').strip()
            _progress['eta'] = d.get('_eta_str', '').strip()
            _progress['downloaded'] = d.get('_downloaded_bytes', 0) or 0
            _progress['total'] = d.get('_total_bytes', 0) or d.get('_total_bytes_estimate', 0) or 0
            _progress['filename'] = d.get('filename', '')
            # Track playlist progress
            if d.get('playlist_index'):
                _progress['playlist_index'] = d['playlist_index']
            if d.get('playlist_count'):
                _progress['playlist_count'] = d['playlist_count']
        elif d['status'] == 'finished':
            if _progress['phase'] != 'done':
                _progress['phase'] = 'finalizing'
            _progress['percent'] = 100.0
            _progress['filename'] = d.get('filename', '')

    ydl_opts = {
        'format': format_str,
        'outtmpl': output_path,
        'quiet': True,
        'no_warnings': True,
        'no_check_certificates': True,
        'geo_bypass': True,
        'progress_hooks': [progress_hook],
        'noplaylist': False,  # Allow playlist download
        'postprocessors': [],
        'playlistend': max_videos,
    }

    if ffmpeg_location and os.path.isdir(ffmpeg_location):
        ydl_opts['ffmpeg_location'] = ffmpeg_location
        ydl_opts['merge_output_format'] = 'mp4'

    if cookies_file and os.path.isfile(cookies_file):
        ydl_opts['cookiefile'] = cookies_file

    try:
        _progress['phase'] = 'downloading'
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        _progress['phase'] = 'done'
        _progress['percent'] = 100.0
        _progress['_done'] = True
        return json.dumps({"success": True})
    except Exception as e:
        _progress['phase'] = 'error'
        _progress['error'] = str(e)
        _progress['_error'] = True
        return json.dumps({"error": str(e)})


# Standalone test
if __name__ == '__main__':
    url = sys.argv[1] if len(sys.argv) > 1 else "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    result = get_video_info(url)
    print(result)
