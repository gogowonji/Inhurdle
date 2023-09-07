# Inhurdle
<img src="https://img.shields.io/badge/Python-3776AB?style=flat&logo=Python&logoColor=white"/><img src="https://img.shields.io/badge/Android-3DDC84?style=flat&logo=Android&logoColor=white"/><img src="https://img.shields.io/badge/GoogleColab-F9AB00?style=flat&logo=GoogleColab&logoColor=white"/>



### ✨ 제작 : 김지원, 이유진
<br>

### "시각장애인을 위한 실시간 장애물 감지 어플"

<br>

### In : 부정형 어미
### Hurdel : 장애물
<br>

> ### 📢 In + Hurdel = Inhurdle : " 장애물을 부정하고 모두가 살기 좋은 삶을 만들다"


<br>

### :point_down: [시작, 사용방법 안내 화면] :point_down:
  *클릭 후 영상 재생*

[![Video Label](https://github.com/gogowonji/Inhurdle/assets/65698313/d350433c-7790-4164-ac19-4b3e2ea66432)](https://drive.google.com/file/d/1SUB2-0UIwRi-XqH2eKPm1vpwLwTw6rIl/view?usp=sharing)

### :point_down: [장애물 감지 및 안내 구현 화면] :point_down:
  *클릭 후 영상 재생*

[![Video Label](https://github.com/gogowonji/Inhurdle/assets/65698313/984753a3-cfa2-4b89-ae8d-df498980eacc)](https://drive.google.com/file/d/19Z0HrJm_VJDSX6g9kozOtMZ-SA1c9o-r/view?usp=sharing)


<br>

## 🗨 Content
### [1. Summary](#pushpin-summary)
### [2. Background](#pushpin-background)
### [3. How to train YOLO](#pushpin-how-to-train-yolo)
### [4. YOLO on Android](#pushpin-yolo-on-android)

<br>

## :pushpin: Summary
실시간 객체 탐지 모델인 YOLO v4를 사용한 실시간 장애물 안내 서비스를 제안


> ***볼라드, 사람, 기둥, 기타 장애물***


시각장애인이 보행 중 마주할 수 있는 장애물을 학습


핸드폰 카메라로 보행로를 스캔, 학습 결과로 장애물이 감지되면 음성으로 안내

<br>

## :pushpin: Background
2022년 12월 31일 기준


- #### 등록된 장애인 : 2,652,860명
- #### 시각 장애인 : 250,767명



전체의 약 <b>10.5%</b>를 차지 [[출처 : “2022년 장애인 등록 현황” 2022, 보건복지부]](https://www.mohw.go.kr/react/jb/sjb030301vw.jsp)


그러나 이들을 위한 상용화되어 있는 서비스가 ***거의 존재하지 않고***, 서비스를 개발하였더라도 실생활에서 쓰이기는 역부족인 실상



### ⇒ 따라서 이러한 한계를 보완한 실시간 장애물 안내 서비스를 제안하게 됨


<br>

## :pushpin: How to train YOLO
### YOLO : 최첨단 실시간 Object Detection 시스템, 물체 감지와 객체 인식에 대한 딥러닝 기반 접근 방식


- #### 클래스


  볼라드(bollard), 사람(person), 기둥(pole), 기타 장애물(etc)

- #### 학습 후 진행한 성능 평가 결과


  |mAP|precision|recall|
  |---|---|---|
  |90.853%|0.91|0.88|


<br>


## :pushpin: YOLO on Android

- ### 시스템 구성도

  <img src="https://github.com/gogowonji/Inhurdle/assets/65698313/0b98e0f1-4853-4ee4-a710-e672618408bc"  width="450" height="200">

  ### 1️⃣ StartActivity

  <img width="273" alt="start" src="https://github.com/gogowonji/Inhurdle/assets/65698313/ce3e44d1-8d8d-4284-b95f-7ec578ea5d45">

  메인 메뉴가 있는 화면으로 전환되기 전 **splash 화면**을 구현

  
  ### 2️⃣ MainActivity

  <img width="264" alt="main" src="https://github.com/gogowonji/Inhurdle/assets/65698313/ea845926-cb07-4071-8cc1-e802c3749141">

  **카메라 시작 버튼**과 **사용 방법 안내** 버튼이 있는 메인 화면
  - #### 사용 방법 안내 음성

    <img src="https://github.com/gogowonji/Inhurdle/assets/65698313/d7fae03e-5b00-43f9-9c66-d5e63bfb6a1b"  width="450" height="200">
  


  
  ### 3️⃣ CameraActivity

  <img width="568" alt="camera" src="https://github.com/gogowonji/Inhurdle/assets/65698313/c7f50341-a5f5-4eb8-9d10-01f9fc5c0429">


  ***YOLO v4***를 구동시키는 가장 중요한 부분

  
  MainActivity에서 카메라 버튼을 누르면 실행

  
  후면 카메라 기능을 허용시켜 사용자에게 자신이 가고 있는 길을 카메라 화면으로 보여줌

  
  장애물을 인식하면 **Bounding Box**로 장애물을 인식했음을 화면에 출력

  
  인식한 장애물을 ***음성*** 으로 안내


<br>
