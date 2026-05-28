// Safe Browser – Media Blocker Extension
// Injects CSS at document_start to hide all images and videos
(function () {
    var css = [
        'img { display: none !important; visibility: hidden !important; }',
        'video, video * { display: none !important; visibility: hidden !important; }',
        'picture, picture * { display: none !important; }',
        'iframe { display: none !important; }',
        '* { background-image: none !important; }'
    ].join('\n');

    var style = document.createElement('style');
    style.id = 'safe-browser-media-block';
    style.textContent = css;

    // Append as early as possible
    var target = document.documentElement || document.head || document.body;
    if (target) {
        target.appendChild(style);
    } else {
        document.addEventListener('DOMContentLoaded', function () {
            (document.head || document.documentElement).appendChild(style);
        });
    }
})();
