"""
YouTube extraction using yt-dlp.
Called from Kotlin via Chaquopy Python bridge.
"""
import json
import os
import sys
import yt_dlp


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


def download_video(url, output_path, format_str, progress_callback=None):
    """
    Download video using yt-dlp.
    format_str: "bestvideo+bestaudio" or "bestaudio" etc.
    output_path: path template like "/path/to/%(title)s.%(ext)s"
    """
    def progress_hook(d):
        if d['status'] == 'downloading':
            percent = d.get('_percent_str', '0%').strip()
            speed = d.get('_speed_str', '')
            eta = d.get('_eta_str', '')
            if progress_callback:
                progress_callback(f"[download] {percent} at {speed} ETA {eta}")
        elif d['status'] == 'finished':
            if progress_callback:
                progress_callback(f"[download] Complete: {d.get('filename', '')}")

    ydl_opts = {
        'format': format_str,
        'outtmpl': output_path,
        'quiet': True,
        'no_warnings': True,
        'no_check_certificates': True,
        'geo_bypass': True,
        'progress_hooks': [progress_hook] if progress_callback else [],
        'merge_output_format': 'mp4',
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
            return json.dumps({"success": True})
    except Exception as e:
        return json.dumps({"error": str(e)})


# Standalone test
if __name__ == '__main__':
    url = sys.argv[1] if len(sys.argv) > 1 else "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    result = get_video_info(url)
    print(result)
