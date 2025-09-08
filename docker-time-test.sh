#!/bin/bash

# ----------------------------
# Docker Metrics Script
# ----------------------------
read -p "Enter Stage Name (e.g., Stage1): " STAGE_NAME
read -p "Enter Dockerfile Type (Single-stage / 2-stage / 3-stage): " TYPE

OUTPUT_FILE="docker_full_report.csv"

# Create CSV with headers if doesn't exist
if [ ! -f $OUTPUT_FILE ]; then
  echo "Stage,Dockerfile_Type,Cold_Build_Backend(s),Cold_Build_Frontend(s),Cold_Build_Total(s),Warm_Build_Backend(s),Warm_Build_Frontend(s),Warm_Build_Total(s),Cold_Startup_Backend(s),Cold_Startup_Frontend(s),Cold_Startup_Total(s),Warm_Startup_Backend(s),Warm_Startup_Frontend(s),Warm_Startup_Total(s),Backend_Mem(MB),Frontend_Mem(MB),Total_Mem(MB),Backend_Size(MB),Frontend_Size(MB),Backend_Layers,Frontend_Layers,Notes" > $OUTPUT_FILE
fi

# ----------------------------
# Function to convert Docker MemUsage to MB
# ----------------------------
mem_to_mb() {
  MEM=$1
  if [[ $MEM == *"MiB"* ]]; then
    echo $MEM | sed 's/MiB//' | awk '{print int($1)}'
  elif [[ $MEM == *"GiB"* ]]; then
    VAL=$(echo $MEM | sed 's/GiB//' | awk '{print $1*1024}')
    echo $VAL | awk '{print int($1)}'
  else
    echo "0"
  fi
}

# ----------------------------
# Clean previous
# ----------------------------
echo "Cleaning images & containers..."
docker-compose down --rmi all --volumes --remove-orphans
docker system prune -af

# ----------------------------
# Cold Build - Backend
# ----------------------------
echo "Cold build backend..."
START=$(date +%s)
docker build -t chattingo-backend ./backend
END=$(date +%s)
COLD_BACKEND_BUILD=$((END-START))

# Cold Build - Frontend
echo "Cold build frontend..."
START=$(date +%s)
docker build -t chattingo-frontend ./frontend
END=$(date +%s)
COLD_FRONTEND_BUILD=$((END-START))

TOTAL_COLD_BUILD=$((COLD_BACKEND_BUILD + COLD_FRONTEND_BUILD))

# ----------------------------
# Cold Compose Up / Startup
# ----------------------------
echo "Cold docker-compose up..."
START=$(date +%s)
docker-compose up -d
# Measure startup times individually
sleep 5 # small buffer
COLD_STARTUP_BACKEND=$(docker logs -f chattingo-backend_1 2>&1 | grep -m1 "Started" &>/dev/null; echo $SECONDS)
COLD_STARTUP_FRONTEND=$(docker logs -f chattingo-frontend_1 2>&1 | grep -m1 "Compiled successfully" &>/dev/null; echo $SECONDS)
COLD_STARTUP_TOTAL=$((COLD_STARTUP_BACKEND + COLD_STARTUP_FRONTEND))
END=$(date +%s)
COLD_COMPOSE_UP=$((END-START))

# ----------------------------
# Warm Build - Backend
# ----------------------------
echo "Warm build backend..."
START=$(date +%s)
docker build -t chattingo-backend ./backend
END=$(date +%s)
WARM_BACKEND_BUILD=$((END-START))

# Warm Build - Frontend
echo "Warm build frontend..."
START=$(date +%s)
docker build -t chattingo-frontend ./frontend
END=$(date +%s)
WARM_FRONTEND_BUILD=$((END-START))

TOTAL_WARM_BUILD=$((WARM_BACKEND_BUILD + WARM_FRONTEND_BUILD))

# ----------------------------
# Warm Compose Up / Startup
# ----------------------------
echo "Warm docker-compose up..."
START=$(date +%s)
docker-compose up -d
sleep 3
WARM_STARTUP_BACKEND=$(docker logs -f chattingo-backend_1 2>&1 | grep -m1 "Started" &>/dev/null; echo $SECONDS)
WARM_STARTUP_FRONTEND=$(docker logs -f chattingo-frontend_1 2>&1 | grep -m1 "Compiled successfully" &>/dev/null; echo $SECONDS)
WARM_STARTUP_TOTAL=$((WARM_STARTUP_BACKEND + WARM_STARTUP_FRONTEND))
END=$(date +%s)
WARM_COMPOSE_UP=$((END-START))

# ----------------------------
# Memory Usage (MB)
# ----------------------------
BACKEND_MEM_RAW=$(docker stats --no-stream --format "{{.MemUsage}}" chattingo-backend)
FRONTEND_MEM_RAW=$(docker stats --no-stream --format "{{.MemUsage}}" chattingo-frontend)
BACKEND_MEM=$(mem_to_mb $BACKEND_MEM_RAW)
FRONTEND_MEM=$(mem_to_mb $FRONTEND_MEM_RAW)
TOTAL_MEM=$((BACKEND_MEM + FRONTEND_MEM))

# ----------------------------
# Image Size (MB)
# ----------------------------
BACKEND_SIZE=$(docker images --format "{{.Repository}} {{.Size}}" chattingo-backend | awk '{print $2}' | sed 's/MB//')
FRONTEND_SIZE=$(docker images --format "{{.Repository}} {{.Size}}" chattingo-frontend | awk '{print $2}' | sed 's/MB//')

# ----------------------------
# Layer Count
# ----------------------------
BACKEND_LAYERS=$(docker history --no-trunc chattingo-backend | wc -l)
FRONTEND_LAYERS=$(docker history --no-trunc chattingo-frontend | wc -l)

# ----------------------------
# Save to CSV
# ----------------------------
echo "$STAGE_NAME,$TYPE,$COLD_BACKEND_BUILD,$COLD_FRONTEND_BUILD,$TOTAL_COLD_BUILD,$WARM_BACKEND_BUILD,$WARM_FRONTEND_BUILD,$TOTAL_WARM_BUILD,$COLD_STARTUP_BACKEND,$COLD_STARTUP_FRONTEND,$COLD_STARTUP_TOTAL,$WARM_STARTUP_BACKEND,$WARM_STARTUP_FRONTEND,$WARM_STARTUP_TOTAL,$BACKEND_MEM,$FRONTEND_MEM,$TOTAL_MEM,$BACKEND_SIZE,$FRONTEND_SIZE,$BACKEND_LAYERS,$FRONTEND_LAYERS,First run / cold build" >> $OUTPUT_FILE

echo "Done! Full metrics saved to $OUTPUT_FILE"
