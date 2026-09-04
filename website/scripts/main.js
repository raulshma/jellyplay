/**
 * JellyPlay Landing Page — Main Script
 * Scroll animations, screenshot carousel, mobile nav, smooth scrolling, and interactive simulators.
 */

(function () {
  'use strict';

  // Theming lives in scripts/themes.js (app design-system port).
  // This file handles page interactions only.

  // ============ SCROLL ANIMATIONS (IntersectionObserver) ============
  function initScrollAnimations() {
    const elements = document.querySelectorAll('.animate-on-scroll');
    if (!elements.length) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            // Add stagger delay based on sibling index within parent
            const siblings = entry.target.parentElement.querySelectorAll('.animate-on-scroll');
            let siblingIndex = 0;
            siblings.forEach((sib, i) => {
              if (sib === entry.target) siblingIndex = i;
            });

            entry.target.style.transitionDelay = `${siblingIndex * 0.08}s`;
            entry.target.classList.add('visible');
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -60px 0px',
      }
    );

    elements.forEach((el) => observer.observe(el));
  }

  // ============ MOBILE NAVIGATION ============
  function initMobileNav() {
    const toggle = document.getElementById('nav-toggle');
    const menu = document.getElementById('mobile-menu');
    const links = document.querySelectorAll('.mobile-link, .mobile-menu a, .nav-logo');

    if (!toggle || !menu) return;

    toggle.addEventListener('click', () => {
      const isOpen = menu.classList.toggle('active');
      const icon = toggle.querySelector('md-icon');
      if (icon) {
        icon.textContent = isOpen ? 'close' : 'menu';
      }
      document.body.style.overflow = isOpen ? 'hidden' : '';
    });

    links.forEach((link) => {
      link.addEventListener('click', () => {
        menu.classList.remove('active');
        const icon = toggle.querySelector('md-icon');
        if (icon) icon.textContent = 'menu';
        document.body.style.overflow = '';
      });
    });
  }

  // ============ NAVBAR SCROLL EFFECT ============
  function initNavScrollEffect() {
    const nav = document.getElementById('site-nav');
    if (!nav) return;

    let ticking = false;

    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          if (window.scrollY > 20) {
            nav.classList.add('scrolled');
          } else {
            nav.classList.remove('scrolled');
          }
          ticking = false;
        });
        ticking = true;
      }
    });
  }

  // ============ SCROLLSPY (active nav link) ============
  function initScrollSpy() {
    const navAnchors = document.querySelectorAll('.nav-links ul a[href^="#"]');
    if (!navAnchors.length) return;

    const sectionToLink = new Map();
    navAnchors.forEach((anchor) => {
      const section = document.querySelector(anchor.getAttribute('href'));
      if (section) sectionToLink.set(section, anchor);
    });
    if (!sectionToLink.size) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            navAnchors.forEach((a) => a.classList.remove('active'));
            const link = sectionToLink.get(entry.target);
            if (link) link.classList.add('active');
          }
        });
      },
      { rootMargin: '-40% 0px -55% 0px' }
    );

    sectionToLink.forEach((_, section) => observer.observe(section));
  }

  // ============ BACK TO TOP ============
  function initBackToTop() {
    const btn = document.getElementById('back-to-top');
    if (!btn) return;

    let ticking = false;
    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          btn.classList.toggle('visible', window.scrollY > 600);
          ticking = false;
        });
        ticking = true;
      }
    }, { passive: true });

    btn.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  // ============ SCREENSHOT CAROUSEL ============
  function initCarousel() {
    const carousel = document.getElementById('screenshot-carousel');
    const navContainer = document.getElementById('carousel-nav');
    if (!carousel || !navContainer) return;

    const cards = carousel.querySelectorAll('.screenshot-card');
    const cardCount = cards.length;
    if (!cardCount) return;

    let activeIndex = 0;
    let autoplayTimer = null;
    const intervalTime = 4000;

    // Create dots
    navContainer.innerHTML = '';
    for (let i = 0; i < cardCount; i++) {
      const dot = document.createElement('button');
      dot.className = 'carousel-dot' + (i === 0 ? ' active' : '');
      dot.setAttribute('aria-label', `Go to screenshot ${i + 1}`);
      dot.addEventListener('click', () => {
        activeIndex = i;
        scrollCardIntoView(i);
        resetAutoplay();
      });
      navContainer.appendChild(dot);
    }

    const dots = navContainer.querySelectorAll('.carousel-dot');

    function scrollCardIntoView(index) {
      const card = cards[index];
      const carouselLeft = carousel.getBoundingClientRect().left;
      const cardLeft = card.getBoundingClientRect().left;
      const offset = cardLeft - carouselLeft + carousel.scrollLeft;
      const centerOffset = offset - (carousel.clientWidth / 2) + (card.clientWidth / 2);
      
      carousel.scrollTo({
        left: centerOffset,
        behavior: 'smooth'
      });
    }

    function startAutoplay() {
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
      if (!autoplayTimer) {
        autoplayTimer = setInterval(() => {
          activeIndex = (activeIndex + 1) % cardCount;
          scrollCardIntoView(activeIndex);
        }, intervalTime);
      }
    }

    function stopAutoplay() {
      if (autoplayTimer) {
        clearInterval(autoplayTimer);
        autoplayTimer = null;
      }
    }

    function resetAutoplay() {
      stopAutoplay();
      startAutoplay();
    }

    // Update active dot on scroll
    const carouselObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const index = Array.from(cards).indexOf(entry.target);
            activeIndex = index;
            dots.forEach((d, i) => {
              d.classList.toggle('active', i === index);
            });
          }
        });
      },
      {
        root: carousel,
        threshold: 0.6,
      }
    );

    cards.forEach((card) => carouselObserver.observe(card));

    // Pause autoplay on hover or touch
    carousel.addEventListener('mouseenter', stopAutoplay);
    carousel.addEventListener('mouseleave', startAutoplay);
    carousel.addEventListener('touchstart', stopAutoplay, { passive: true });
    carousel.addEventListener('touchend', startAutoplay, { passive: true });

    // Keyboard support
    carousel.setAttribute('tabindex', '0');
    carousel.addEventListener('keydown', (e) => {
      const scrollAmount = 320;
      if (e.key === 'ArrowRight') {
        carousel.scrollBy({ left: scrollAmount, behavior: 'smooth' });
        resetAutoplay();
      } else if (e.key === 'ArrowLeft') {
        carousel.scrollBy({ left: -scrollAmount, behavior: 'smooth' });
        resetAutoplay();
      }
    });

    // Arrow buttons
    const prevBtn = document.getElementById('carousel-prev');
    const nextBtn = document.getElementById('carousel-next');
    const step = () => (cards[0] ? cards[0].clientWidth + 24 : 320);
    if (prevBtn) prevBtn.addEventListener('click', () => {
      carousel.scrollBy({ left: -step(), behavior: 'smooth' });
      resetAutoplay();
    });
    if (nextBtn) nextBtn.addEventListener('click', () => {
      carousel.scrollBy({ left: step(), behavior: 'smooth' });
      resetAutoplay();
    });

    // Start autoplay
    startAutoplay();
  }

  // ============ FAQ ACCORDIONS ============
  function initFAQs() {
    const faqItems = document.querySelectorAll('.faq-item');
    faqItems.forEach((item) => {
      const trigger = item.querySelector('.faq-trigger');
      const content = item.querySelector('.faq-content');

      if (!trigger || !content) return;

      trigger.addEventListener('click', () => {
        const isActive = item.classList.contains('active');

        // Close all other FAQs
        faqItems.forEach((otherItem) => {
          if (otherItem !== item && otherItem.classList.contains('active')) {
            otherItem.classList.remove('active');
            otherItem.querySelector('.faq-trigger').setAttribute('aria-expanded', 'false');
            otherItem.querySelector('.faq-content').style.maxHeight = '0px';
          }
        });

        // Toggle active item
        if (isActive) {
          item.classList.remove('active');
          trigger.setAttribute('aria-expanded', 'false');
          content.style.maxHeight = '0px';
        } else {
          item.classList.add('active');
          trigger.setAttribute('aria-expanded', 'true');
          content.style.maxHeight = content.scrollHeight + 'px';
        }
      });
    });
  }

  // ============ SMOOTH SCROLL FOR ANCHOR LINKS ============
  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
      anchor.addEventListener('click', (e) => {
        const targetId = anchor.getAttribute('href');
        if (targetId === '#') return;

        const target = document.querySelector(targetId);
        if (target) {
          e.preventDefault();
          const navHeight = 64;
          const targetPosition = target.getBoundingClientRect().top + window.scrollY - navHeight;

          window.scrollTo({
            top: targetPosition,
            behavior: 'smooth',
          });
        }
      });
    });
  }

  // ============ PARALLAX HERO GLOW ============
  function initHeroParallax() {
    const heroGlow = document.querySelector('.hero-glow');
    if (!heroGlow || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    window.addEventListener('mousemove', (e) => {
      const x = (e.clientX / window.innerWidth - 0.5) * 20;
      const y = (e.clientY / window.innerHeight - 0.5) * 20;
      heroGlow.style.transform = `translate(${x}px, ${y}px)`;
    });
  }

  // ============ GITHUB STARS ============
  async function initGithubStars() {
    const starsCountEl = document.getElementById('github-stars-count');
    if (!starsCountEl) return;
    try {
      const response = await fetch('https://api.github.com/repos/raulshma/JellyPlay');
      if (response.ok) {
        const data = await response.json();
        const stars = data.stargazers_count;
        if (stars !== undefined) {
          starsCountEl.textContent = stars;
        }
      }
    } catch (e) {
      console.error('Failed to fetch GitHub stars', e);
    }
  }

  // ============ DIRECT RELEASE DOWNLOADS & MODAL ============
  async function initReleaseAndDownloads() {
    const navBadge = document.getElementById('nav-release-badge');
    const navVersionEl = document.getElementById('nav-release-version');
    const modalVersionEl = document.getElementById('modal-release-version');
    const phoneList = document.getElementById('phone-download-list');
    const tvList = document.getElementById('tv-download-list');
    
    // Dropdown selectors
    const dropdownList = document.getElementById('dropdown-list');
    const dropdownToggleBtn = document.getElementById('hero-download-btn-right');
    const dropdownMenu = document.getElementById('download-dropdown-menu');
    
    const modal = document.getElementById('download-modal');
    const closeBtn = document.getElementById('modal-close-btn');
    const triggerBtns = document.querySelectorAll('.trigger-download-modal');
    
    if (!navBadge || !navVersionEl || !modalVersionEl || !phoneList || !tvList || !modal) return;

    // --- 1. Dropdown Toggle and Document Clicks ---
    if (dropdownToggleBtn && dropdownMenu) {
      dropdownToggleBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        const expanded = dropdownToggleBtn.getAttribute('aria-expanded') === 'true';
        dropdownToggleBtn.setAttribute('aria-expanded', !expanded);
        dropdownMenu.classList.toggle('active', !expanded);
      });
      
      // Close dropdown when clicking outside
      document.addEventListener('click', (e) => {
        if (!dropdownMenu.contains(e.target) && e.target !== dropdownToggleBtn) {
          dropdownToggleBtn.setAttribute('aria-expanded', 'false');
          dropdownMenu.classList.remove('active');
        }
      });

      // Prevent closing when clicking inside the dropdown header or sections
      dropdownMenu.addEventListener('click', (e) => {
        if (!e.target.closest('.download-item-btn') && !e.target.closest('.download-item-btn span')) {
          e.stopPropagation();
        }
      });
    }

    // --- 2. Modal open/close listeners (Attach synchronously at startup) ---
    function openModal() {
      modal.classList.add('active');
      document.body.style.overflow = 'hidden';
    }

    function closeModal() {
      modal.classList.remove('active');
      document.body.style.overflow = '';
    }

    triggerBtns.forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        
        // If mobile menu is open, close it first
        const mobileMenu = document.getElementById('mobile-menu');
        const navToggle = document.getElementById('nav-toggle');
        if (mobileMenu && mobileMenu.classList.contains('active')) {
          mobileMenu.classList.remove('active');
          if (navToggle) {
            const icon = navToggle.querySelector('md-icon');
            if (icon) icon.textContent = 'menu';
          }
        }
        
        openModal();
      });
    });

    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeModal();
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        if (modal.classList.contains('active')) {
          closeModal();
        }
        if (dropdownToggleBtn && dropdownMenu && dropdownMenu.classList.contains('active')) {
          dropdownToggleBtn.setAttribute('aria-expanded', 'false');
          dropdownMenu.classList.remove('active');
        }
      }
    });

    // Populate fallback downloads list initially so clicking triggers actual files instantly
    const fallbackData = getFallbackData();
    populateDownloads(fallbackData);

    // --- 3. Fetch Release Data asynchronously in background ---
    const cacheKey = 'jellyplay_latest_release';
    const cacheDuration = 3600000; // 1 hour
    let releaseData = null;

    try {
      const cached = sessionStorage.getItem(cacheKey);
      if (cached) {
        const parsed = JSON.parse(cached);
        if (Date.now() - parsed.timestamp < cacheDuration) {
          releaseData = parsed.data;
        }
      }
    } catch (e) {
      console.warn('Failed to read release cache', e);
    }

    if (!releaseData) {
      try {
        const response = await fetch('https://api.github.com/repos/raulshma/JellyPlay/releases/latest');
        if (response.ok) {
          const data = await response.json();
          releaseData = {
            tag: data.tag_name,
            assets: data.assets.map(a => ({
              name: a.name,
              url: a.browser_download_url,
              size: a.size
            }))
          };
          // Save to cache
          try {
            sessionStorage.setItem(cacheKey, JSON.stringify({
              data: releaseData,
              timestamp: Date.now()
            }));
          } catch (e) {}
        }
      } catch (e) {
        console.error('Failed to fetch release from GitHub API', e);
      }
    }

    if (releaseData) {
      populateDownloads(releaseData);
    }

    function populateDownloads(data) {
      navVersionEl.textContent = data.tag;
      modalVersionEl.textContent = data.tag;
      navBadge.style.display = 'inline-flex';

      phoneList.innerHTML = '';
      tvList.innerHTML = '';
      if (dropdownList) dropdownList.innerHTML = '';

      let phoneCount = 0;
      let tvCount = 0;
      let dropdownCount = 0;

      data.assets.forEach(asset => {
        if (asset.name.endsWith('.apk')) {
          const modalHtml = createDownloadItemHTML(asset);
          if (asset.name.includes('-phone-')) {
            phoneList.insertAdjacentHTML('beforeend', modalHtml);
            phoneCount++;
          } else if (asset.name.includes('-tv-')) {
            tvList.insertAdjacentHTML('beforeend', modalHtml);
            tvCount++;
          }

          if (dropdownList) {
            const dropdownHtml = createCompactDropdownItemHTML(asset);
            dropdownList.insertAdjacentHTML('beforeend', dropdownHtml);
            dropdownCount++;
          }
        }
      });

      if (phoneCount === 0) {
        phoneList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No phone APKs found.</p>`;
      }
      if (tvCount === 0) {
        tvList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No TV APKs found.</p>`;
      }
      if (dropdownCount === 0 && dropdownList) {
        dropdownList.innerHTML = `<p style="color: var(--jp-white-alpha-40); text-align: center; font-size: 0.8125rem; padding: 12px 0;">No downloads found.</p>`;
      }
    }
  }

  function formatBytes(bytes) {
    if (!bytes) return '';
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  }

  function createDownloadItemHTML(asset) {
    let archLabel = 'Universal';
    let archDesc = 'All devices';
    let isRecommended = false;
    
    if (asset.name.includes('universal')) {
      archLabel = 'Universal';
      archDesc = 'All architectures';
      isRecommended = true;
    } else if (asset.name.includes('arm64-v8a')) {
      archLabel = 'ARM64';
      archDesc = '64-bit phones & TV';
    } else if (asset.name.includes('x86_64')) {
      archLabel = 'x86_64';
      archDesc = 'Intel / Emulator';
    }

    const sizeStr = asset.size ? ` • ${formatBytes(asset.size)}` : '';
    const recText = isRecommended ? '<span class="download-item-name-rec">Recommended</span>' : '';
    
    return `
      <div class="download-item">
        <div class="download-item-info">
          <div class="download-item-name">
            ${archLabel}
            ${recText}
          </div>
          <div class="download-item-meta">${archDesc}${sizeStr}</div>
        </div>
        <a href="${asset.url}" class="download-item-btn" aria-label="Download ${archLabel} version" download>
          <span class="material-symbols-outlined">download</span>
        </a>
      </div>
    `;
  }

  function createCompactDropdownItemHTML(asset) {
    let deviceIcon = 'phone_android';
    let deviceTitle = 'Phone & Tablet Build';
    if (asset.name.includes('-tv-')) {
      deviceIcon = 'tv';
      deviceTitle = 'Android TV Build';
    }
    
    let archLabel = 'Universal';
    if (asset.name.includes('universal')) {
      archLabel = 'Universal';
    } else if (asset.name.includes('arm64-v8a')) {
      archLabel = 'ARM64';
    } else if (asset.name.includes('x86_64')) {
      archLabel = 'x86_64';
    }

    const sizeStr = asset.size ? formatBytes(asset.size) : 'APK';
    
    return `
      <a href="${asset.url}" class="compact-dropdown-item" download>
        <span class="dropdown-item-left">
          <md-icon class="device-icon" title="${deviceTitle}">${deviceIcon}</md-icon>
          <md-icon class="android-icon" title="Android Installer (APK)">android</md-icon>
          <span class="dropdown-item-title">${archLabel}</span>
        </span>
        <span class="dropdown-item-info">${sizeStr}</span>
      </a>
    `;
  }

  function getFallbackData() {
    const fallbackVersion = 'v0.5.8';
    const architectures = ['universal', 'arm64-v8a', 'x86_64'];
    const types = ['phone', 'tv'];
    const assets = [];
    
    types.forEach(type => {
      architectures.forEach(arch => {
        const filename = `jellyplay-${fallbackVersion}-${type}-${arch}.apk`;
        assets.push({
          name: filename,
          url: `https://github.com/raulshma/JellyPlay/releases/download/${fallbackVersion}/${filename}`,
          size: null
        });
      });
    });
    
    return {
      tag: fallbackVersion,
      assets: assets
    };
  }

  // ============ SPOTLIGHT GLOW EFFECT ============
  function initSpotlightGlow() {
    const cards = document.querySelectorAll('.feature-card');
    cards.forEach(card => {
      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        card.style.setProperty('--mouse-x', `${x}px`);
        card.style.setProperty('--mouse-y', `${y}px`);
      });
    });
  }

  // ============ VIDEO ENGINE SWITCHER ============
  function initVideoEngineSwitcher() {
    const chips = document.querySelectorAll('.engine-chip');
    const badge = document.getElementById('player-engine-badge');
    const specs = document.getElementById('player-engine-specs');
    const progress = document.querySelector('.mock-player-progress-bar');
    
    if (!chips.length || !badge || !specs) return;
    
    const engineData = {
      exo: {
        name: 'ExoPlayer Engine',
        progress: '38%',
        specs: [
          '<span><md-icon>hdr_on</md-icon> HDR10+</span>',
          '<span><md-icon>picture_in_picture</md-icon> PiP Mode</span>',
          '<span><md-icon>gesture</md-icon> Gestures</span>',
          '<span><md-icon>speed</md-icon> Auto-Bitrate</span>'
        ]
      },
      mpv: {
        name: 'libmpv Engine',
        progress: '72%',
        specs: [
          '<span><md-icon>subtitles</md-icon> ASS Softsubs</span>',
          '<span><md-icon>fit_screen</md-icon> Custom Aspect</span>',
          '<span><md-icon>filter_b_and_w</md-icon> Video Filters</span>',
          '<span><md-icon>volume_up</md-icon> Audio Boost</span>'
        ]
      },
      vlc: {
        name: 'LibVLC Engine',
        progress: '15%',
        specs: [
          '<span><md-icon>cast</md-icon> Chromecast</span>',
          '<span><md-icon>lan</md-icon> Network Stream</span>',
          '<span><md-icon>music_video</md-icon> Disc ISO Play</span>',
          '<span><md-icon>slow_motion_video</md-icon> Pitch Correct</span>'
        ]
      }
    };
    
    chips.forEach(chip => {
      chip.addEventListener('click', () => {
        chips.forEach(c => {
          c.classList.remove('active');
          c.setAttribute('aria-selected', 'false');
        });
        chip.classList.add('active');
        chip.setAttribute('aria-selected', 'true');
        
        const engine = chip.dataset.engine;
        const data = engineData[engine];
        
        badge.textContent = data.name;
        if (progress) progress.style.width = data.progress;
        specs.innerHTML = data.specs.join('');
      });
    });
  }

  // ============ RICH AUDIO PLAYER SIMULATOR ============
  function initAudioSimulator() {
    const toggleBtn = document.getElementById('audio-play-toggle');
    const toggleIcon = document.getElementById('audio-play-icon');
    const vinyl = document.getElementById('audio-vinyl');
    const visualizer = document.getElementById('audio-visualizer');
    const lyricsWrapper = document.querySelector('.lyrics-wrapper');
    
    if (!toggleBtn || !vinyl || !visualizer || !lyricsWrapper) return;
    
    let isPlaying = false;
    let animationInterval = null;
    let lyricsInterval = null;
    let lyricIndex = 0;
    const bars = visualizer.querySelectorAll('.bar');
    
    const lyrics = [
      { text: "Midnight Stream is now playing...", active: true },
      { text: "Synchronized lyrics flowing in real-time", active: false },
      { text: "Ambient color blobs shift with the rhythm", active: false },
      { text: "Equalizer bands auto-tune to your mood", active: false }
    ];
    
    function updateVisualizer() {
      bars.forEach(bar => {
        const height = isPlaying ? Math.floor(Math.random() * 85) + 15 : 10;
        bar.style.height = `${height}%`;
      });
    }
    
    function updateLyrics() {
      lyricIndex = (lyricIndex + 1) % lyrics.length;
      
      lyricsWrapper.innerHTML = '';
      lyrics.forEach((lyric, idx) => {
        const p = document.createElement('p');
        p.className = 'lyric-line' + (idx === lyricIndex ? ' active' : '');
        p.textContent = lyric.text;
        lyricsWrapper.appendChild(p);
      });
      
      const offset = 12 - (lyricIndex * 24);
      lyricsWrapper.style.transform = `translateY(${offset}px)`;
    }
    
    toggleBtn.addEventListener('click', () => {
      isPlaying = !isPlaying;
      
      if (isPlaying) {
        toggleIcon.textContent = 'pause';
        vinyl.classList.add('playing');
        vinyl.classList.remove('paused');
        visualizer.classList.add('playing');
        
        animationInterval = setInterval(updateVisualizer, 150);
        lyricsInterval = setInterval(updateLyrics, 3000);
        updateLyrics();
      } else {
        toggleIcon.textContent = 'play_arrow';
        vinyl.classList.remove('playing');
        vinyl.classList.add('paused');
        visualizer.classList.remove('playing');
        
        clearInterval(animationInterval);
        clearInterval(lyricsInterval);
        
        bars.forEach(bar => bar.style.height = '10%');
      }
    });
  }

  // ============ ANDROID TV SIMULATOR ============
  function initTVSimulator() {
    const dpadUp = document.getElementById('dpad-up');
    const dpadDown = document.getElementById('dpad-down');
    const dpadLeft = document.getElementById('dpad-left');
    const dpadRight = document.getElementById('dpad-right');
    const dpadOk = document.getElementById('dpad-ok');
    const tvItems = document.querySelectorAll('.tv-item');
    const previewText = document.getElementById('tv-preview-text');
    
    if (!tvItems.length || !previewText) return;
    
    const items = Array.from(tvItems);
    let activeIndex = 0;
    
    const itemDescriptions = {
      movies: 'Browse your 4K Ultra HD movie library',
      tv: 'Stream multi-season TV shows with auto-next play',
      music: 'Listen to artists and custom audio playlists',
      settings: 'Customize display, playback engines, and styling'
    };
    
    function updateTVSelection(newIndex) {
      items[activeIndex].classList.remove('active');
      activeIndex = (newIndex + items.length) % items.length;
      items[activeIndex].classList.add('active');
      
      const key = items[activeIndex].dataset.tvItem;
      previewText.textContent = itemDescriptions[key] || '';
      previewText.style.animation = 'none';
      void previewText.offsetWidth;
      previewText.style.animation = 'fade-in 0.3s ease';
    }
    
    if (dpadUp) dpadUp.addEventListener('click', () => updateTVSelection(activeIndex - 2));
    if (dpadDown) dpadDown.addEventListener('click', () => updateTVSelection(activeIndex + 2));
    if (dpadLeft) dpadLeft.addEventListener('click', () => updateTVSelection(activeIndex - 1));
    if (dpadRight) dpadRight.addEventListener('click', () => updateTVSelection(activeIndex + 1));
    
    if (dpadOk) {
      dpadOk.addEventListener('click', () => {
        const key = items[activeIndex].dataset.tvItem;
        previewText.innerHTML = `<strong>Selected: ${key.toUpperCase()}</strong>`;
        setTimeout(() => {
          previewText.textContent = itemDescriptions[key];
        }, 1500);
      });
    }
    
    const tvCard = document.getElementById('feature-tv');
    if (tvCard) {
      tvCard.addEventListener('keydown', (e) => {
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Enter'].includes(e.key)) {
          e.preventDefault();
          if (e.key === 'ArrowUp') updateTVSelection(activeIndex - 2);
          if (e.key === 'ArrowDown') updateTVSelection(activeIndex + 2);
          if (e.key === 'ArrowLeft') updateTVSelection(activeIndex - 1);
          if (e.key === 'ArrowRight') updateTVSelection(activeIndex + 1);
          if (e.key === 'Enter') dpadOk.click();
        }
      });
      tvCard.setAttribute('tabindex', '0');
    }
  }

  // ============ SYNCPLAY WATCH PARTY SIMULATOR ============
  function initSyncPlaySimulator() {
    const driftBtn = document.getElementById('syncplay-drift-btn');
    const badge = document.getElementById('syncplay-status-badge');
    const delayVal = document.getElementById('syncplay-delay-val');
    const progressAlex = document.getElementById('sync-progress-alex');
    const progressYou = document.getElementById('sync-progress-you');
    
    if (!driftBtn || !badge || !delayVal || !progressAlex || !progressYou) return;
    
    let isSimulating = false;
    
    driftBtn.addEventListener('click', () => {
      if (isSimulating) return;
      isSimulating = true;
      driftBtn.disabled = true;
      driftBtn.textContent = 'Simulating playback drift...';
      
      badge.textContent = 'DRIFT DETECTED';
      badge.className = 'syncplay-badge danger';
      delayVal.textContent = '420ms delay';
      progressYou.style.width = '55%';
      
      setTimeout(() => {
        badge.textContent = 'SYNCING (+5% speed)';
        badge.className = 'syncplay-badge warning';
        delayVal.textContent = '180ms delay';
        progressYou.style.width = '57%';
        
        setTimeout(() => {
          badge.textContent = 'IN SYNC';
          badge.className = 'syncplay-badge success';
          delayVal.textContent = '0ms delay';
          progressYou.style.width = '58%';
          driftBtn.disabled = false;
          driftBtn.textContent = 'Simulate Playback Drift';
          isSimulating = false;
        }, 2000);
      }, 2000);
    });
  }

  // ============ MINOR WIDGETS INTERACTIVE LOOP ============
  function initMinorWidgets() {
    const cpuVal = document.getElementById('admin-cpu-val');
    const adminCard = document.getElementById('feature-admin');
    if (cpuVal && adminCard) {
      setInterval(() => {
        const isUserHovering = adminCard.matches(':hover');
        if (isUserHovering) {
          const val = Math.floor(Math.random() * 30) + 15;
          cpuVal.textContent = `${val}%`;
        } else {
          const val = Math.floor(Math.random() * 8) + 12;
          cpuVal.textContent = `${val}%`;
        }
      }, 2500);
    }
    
    const dlCard = document.getElementById('feature-downloads');
    const dlPct = document.getElementById('dl-percentage');
    const dlBar = document.getElementById('dl-bar');
    if (dlCard && dlPct && dlBar) {
      dlCard.addEventListener('mouseenter', () => {
        let pct = 78;
        const interval = setInterval(() => {
          if (pct < 100) {
            pct += 1;
            dlPct.textContent = `${pct}%`;
            dlBar.style.width = `${pct}%`;
          } else {
            clearInterval(interval);
            setTimeout(() => {
              dlPct.textContent = '78%';
              dlBar.style.width = '78%';
            }, 2000);
          }
        }, 80);
        
        dlCard.addEventListener('mouseleave', () => {
          clearInterval(interval);
          dlPct.textContent = '78%';
          dlBar.style.width = '78%';
        }, { once: true });
      });
    }
  }

  // ============ INITIALIZE ============
  function init() {
    initScrollAnimations();
    initMobileNav();
    initNavScrollEffect();
    initCarousel();
    initFAQs();
    initSmoothScroll();
    initHeroParallax();
    initGithubStars();
    initReleaseAndDownloads();
    initSpotlightGlow();
    initVideoEngineSwitcher();
    initAudioSimulator();
    initTVSimulator();
    initSyncPlaySimulator();
    initMinorWidgets();
    initScrollSpy();
    initBackToTop();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
