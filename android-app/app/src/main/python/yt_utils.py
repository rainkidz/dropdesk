"""
YouTube extraction using yt-dlp.
Called from Kotlin via Chaquopy Python bridge.

Progress is kept per job_id so several downloads (e.g. a premium batch queue)
can run in parallel without overwriting each other's state. Every download
function accepts a trailing ``job_id`` argument; Kotlin polls the matching id
with ``get_progress(job_id)``.
"""
import json
import os
import sys
import yt_dlp

# Per-job progress registry — keyed by job_id, polled by Kotlin via get_progress(job_id)
_jobs = {}
_EMPTY_STATE = {
    "phase": "idle",
    "percent": 0.0,
    "speed": "",
    "eta": "",
    "downloaded": 0,
    "total": 0,
    "filename": "",
    "error": "",
}


def _state(job_id=""):
    """Return the mutable state dict for a job, creating it on first use."""
    key = job_id or ""
    st = _jobs.get(key)
    if st is None:
        st = dict(_EMPTY_STATE)
        st["_done"] = False
        st["_error"] = False
        _jobs[key] = st
    return st


def _reset(job_id=""):
    """Reset a job to its extracting/not-done state."""
    st = _state(job_id)
    st.update(_EMPTY_STATE)
    st["phase"] = "extracting"
    st["_done"] = False
    st["_error"] = False
    return st


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


def get_progress(job_id=""):
    """Return progress for one job as a JSON string. Called by Kotlin polling."""
    return json.dumps(_state(job_id))


def download_video(url, output_path, format_str, cookies_file="", job_id=""):
    """
    Download a single video/audio stream using yt-dlp.
    format_str: "bestvideo" or "bestaudio" etc.
    output_path: path template like "/path/to/%(title)s.%(ext)s"
    cookies_file: path to Netscape cookies.txt file for authentication
    job_id: unique id so parallel jobs don't share progress state
    """
    st = _reset(job_id)

    def progress_hook(d):
        if d['status'] == 'downloading':
            st['phase'] = 'downloading'
            try:
                st['percent'] = float(d.get('_percent_str', '0%').strip().replace('%', '').strip())
            except (ValueError, AttributeError):
                st['percent'] = 0.0
            st['speed'] = d.get('_speed_str', '').strip()
            st['eta'] = d.get('_eta_str', '').strip()
            st['downloaded'] = d.get('_downloaded_bytes', 0) or 0
            st['total'] = d.get('_total_bytes', 0) or d.get('_total_bytes_estimate', 0) or 0
            st['filename'] = d.get('filename', '')
        elif d['status'] == 'finished':
            # Only mark as finalizing on first finish — don't reset to downloading
            if st['phase'] != 'done':
                st['phase'] = 'finalizing'
            st['percent'] = 100.0
            st['filename'] = d.get('filename', '')

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
        st['phase'] = 'downloading'
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        st['phase'] = 'done'
        st['percent'] = 100.0
        st['_done'] = True
        return json.dumps({"success": True})
    except Exception as e:
        st['phase'] = 'error'
        st['error'] = str(e)
        st['_error'] = True
        return json.dumps({"error": str(e)})


def download_video_audio(url, output_path, video_format, audio_format="bestaudio", cookies_file="", ffmpeg_location="", job_id=""):
    """
    Download video + audio and merge using ffmpeg.
    video_format: e.g. "bestvideo[height<=1080][ext=mp4]"
    audio_format: e.g. "bestaudio[ext=m4a]"
    This is for premium users who want 1080p+ with audio.
    job_id: unique id so parallel jobs don't share progress state
    """
    st = _reset(job_id)

    def progress_hook(d):
        if d['status'] == 'downloading':
            st['phase'] = 'downloading'
            try:
                st['percent'] = float(d.get('_percent_str', '0%').strip().replace('%', '').strip())
            except (ValueError, AttributeError):
                st['percent'] = 0.0
            st['speed'] = d.get('_speed_str', '').strip()
            st['eta'] = d.get('_eta_str', '').strip()
            st['downloaded'] = d.get('_downloaded_bytes', 0) or 0
            st['total'] = d.get('_total_bytes', 0) or d.get('_total_bytes_estimate', 0) or 0
            st['filename'] = d.get('filename', '')
        elif d['status'] == 'finished':
            if st['phase'] != 'done':
                st['phase'] = 'merging'  # Merging video + audio
            st['percent'] = 100.0
            st['filename'] = d.get('filename', '')

    # Merge format: bestvideo + bestaudio → single file
    merge_format = "{}+{}".format(video_format, audio_format)

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
        st['phase'] = 'downloading'
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        st['phase'] = 'done'
        st['percent'] = 100.0
        st['_done'] = True
        return json.dumps({"success": True})
    except Exception as e:
        st['phase'] = 'error'
        st['error'] = str(e)
        st['_error'] = True
        return json.dumps({"error": str(e)})


def download_playlist(url, output_path, format_str, max_videos=50, cookies_file="", ffmpeg_location="", job_id=""):
    """
    Download entire playlist.
    format_str: "bestvideo+bestaudio" or "bestaudio" etc.
    max_videos: limit number of videos to download
    job_id: unique id so parallel jobs don't share progress state
    """
    st = _reset(job_id)

    def progress_hook(d):
        if d['status'] == 'downloading':
            st['phase'] = 'downloading'
            try:
                st['percent'] = float(d.get('_percent_str', '0%').strip().replace('%', '').strip())
            except (ValueError, AttributeError):
                st['percent'] = 0.0
            st['speed'] = d.get('_speed_str', '').strip()
            st['eta'] = d.get('_eta_str', '').strip()
            st['downloaded'] = d.get('_downloaded_bytes', 0) or 0
            st['total'] = d.get('_total_bytes', 0) or d.get('_total_bytes_estimate', 0) or 0
            st['filename'] = d.get('filename', '')
            # Track playlist progress
            if d.get('playlist_index'):
                st['playlist_index'] = d['playlist_index']
            if d.get('playlist_count'):
                st['playlist_count'] = d['playlist_count']
        elif d['status'] == 'finished':
            if st['phase'] != 'done':
                st['phase'] = 'finalizing'
            st['percent'] = 100.0
            st['filename'] = d.get('filename', '')

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
        st['phase'] = 'downloading'
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        st['phase'] = 'done'
        st['percent'] = 100.0
        st['_done'] = True
        return json.dumps({"success": True})
    except Exception as e:
        st['phase'] = 'error'
        st['error'] = str(e)
        st['_error'] = True
        return json.dumps({"error": str(e)})


# Standalone test
if __name__ == '__main__':
    url = sys.argv[1] if len(sys.argv) > 1 else "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    result = get_video_info(url)
    print(result)
