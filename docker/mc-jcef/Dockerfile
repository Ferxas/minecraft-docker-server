# Paper image + native libs required by MapBrowser/JCEF (Chromium + JogAmp GL).
# Base: https://github.com/itzg/docker-minecraft-server
FROM itzg/minecraft-server:latest

USER root

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      libnspr4 \
      libnss3 \
      libasound2t64 \
      libatk1.0-0t64 \
      libatk-bridge2.0-0t64 \
      libcups2t64 \
      libdrm2 \
      libgbm1 \
      libgtk-3-0t64 \
      libpango-1.0-0 \
      libcairo2 \
      libx11-6 \
      libx11-xcb1 \
      libxcb1 \
      libxcomposite1 \
      libxdamage1 \
      libxext6 \
      libxfixes3 \
      libxkbcommon0 \
      libxrandr2 \
      libxrender1 \
      libxshmfence1 \
      libxtst6 \
      libxxf86vm1 \
      libgl1 \
      libglx-mesa0 \
      libegl1 \
      libgles2 \
      mesa-utils \
      mesa-vulkan-drivers \
      xvfb \
      xserver-xorg-core \
      xserver-xorg-video-dummy \
      x11-xserver-utils \
      gcc \
      libc6-dev \
      fonts-liberation \
      ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# jawt ships with Temurin; put it on the default linker path for JCEF.
RUN ln -sf /opt/java/openjdk/lib/libjawt.so /usr/lib/libjawt.so \
 && ln -sf /opt/java/openjdk/lib/server/libjvm.so /usr/lib/libjvm.so || true

# Allow non-console Xorg (dummy) inside the container.
RUN mkdir -p /etc/X11 \
 && printf 'allowed_users=anybody\nneeds_root_rights=yes\n' > /etc/X11/Xwrapper.config

COPY xorg-dummy.conf /etc/X11/xorg-dummy.conf
COPY glx_query_shim.c /tmp/glx_query_shim.c
RUN gcc -shared -fPIC -O2 -o /usr/local/lib/libglx_query_shim.so /tmp/glx_query_shim.c -ldl \
 && rm -f /tmp/glx_query_shim.c \
 && ldconfig
# Note: shim must intercept dlsym("glXGetProcAddress") — JogAmp resolves GLX that way.

ENV LD_LIBRARY_PATH=/opt/java/openjdk/lib:/opt/java/openjdk/lib/server
ENV DISPLAY=:99
ENV LIBGL_ALWAYS_SOFTWARE=1
ENV GALLIUM_DRIVER=llvmpipe
ENV LD_PRELOAD=/usr/local/lib/libglx_query_shim.so

COPY entrypoint-xvfb.sh /entrypoint-xvfb.sh
RUN chmod +x /entrypoint-xvfb.sh \
 && sed -i 's/\r$//' /entrypoint-xvfb.sh /etc/X11/xorg-dummy.conf

ENTRYPOINT ["/entrypoint-xvfb.sh"]
