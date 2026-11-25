# Inline file preview (/api/file/public/view): allow same-origin iframe embedding.
# PDF preview uses <iframe>; the global X-Frame-Options: DENY and
# CSP frame-ancestors 'none' make Chrome refuse to render it.
# NOTE: add_header in a location REPLACES all http-level add_header, so every
# security header must be redeclared here (frame-related ones relaxed to self).
location = /api/file/public/view {
    limit_req zone=api_limit burst=30 nodelay;
    limit_req_status 429;
    proxy_pass http://127.0.0.1:9080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
    # Spring Security emits X-Frame-Options: DENY upstream; drop it before re-adding
    proxy_hide_header X-Frame-Options;
    proxy_hide_header Content-Security-Policy;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Content-Security-Policy "object-src 'none'; base-uri 'self'; frame-ancestors 'self'" always;
    add_header Strict-Transport-Security "max-age=31536000" always;
}
