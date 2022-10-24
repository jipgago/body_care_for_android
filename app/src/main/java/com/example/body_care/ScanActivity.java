package com.example.body_care;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.body_care.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class ScanActivity extends Activity {

    protected Interpreter tflite,ableModel,APTmodel,PPTmodel; // 모델들 선언
    private MappedByteBuffer tfliteModel;
    private TensorImage inputImageBuffer;
    private int imageSizeX;
    private int imageSizeY;
    private TensorBuffer outputProbabilityBuffer;
    private TensorProcessor probabilityProcessor;
    private static final float IMAGE_MEAN = 0.0f;
    private static final float IMAGE_STD = 1.0f;
    private static final float PROBABILITY_MEAN = 0.0f;
    private static final float PROBABILITY_STD = 255.0f;
    private Bitmap bitmap;
    private List<String> labels;
    private String sol;

    ImageView imgView;
    Uri imgUri;
    Button chooseBtn, predictBtn, btnCapture, btnSolution;
    TextView txtResult;

    int[] ResultArr;
    LinearLayout text,no_text,front_pelvis,no_front,post_pelvis,no_post;

    Uri photoUri;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        imgView = (ImageView) findViewById(R.id.showImg);
        chooseBtn = (Button) findViewById(R.id.choosePic);
        predictBtn = (Button) findViewById(R.id.predict);
        txtResult = (TextView) findViewById(R.id.showResult);
        btnCapture = (Button) findViewById(R.id.btnCapture1);
        btnSolution = (Button) findViewById(R.id.btnSolution);
        ResultArr = new int[4]; // 거북목 여부 - 골반측정가능 여부 - 전방경사 여부 - 후방경사 여부 순서이다.
        for(int i = 0; i < 4 ; i++){
            ResultArr[i] = 2; // 측정 전 상태로 초기화
        }
        // 0 - 해당하지 않음, 1 - 해당함, 2 - 측정 전 상태

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent,0);
            }
        });


        chooseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"Select Picture"),12);
            }
        });

        btnSolution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), StartActivity.class);
                intent.putExtra("et1", sol);
                startActivity(intent);
            }
        });

        //define new interpreter with tflite model

        try {
            tflite = new Interpreter(loadmodelfile(ScanActivity.this)); // 거북목 판단 여부 모델 불러오기
            ableModel = new Interpreter(loadAbleModelFile(ScanActivity.this)); // 골반 진단여부 판단 모델 불러오기
            APTmodel = new Interpreter(loadAPTModelFile(ScanActivity.this)); // 골반전방경사 판단 모델
            PPTmodel = new Interpreter(loadPPTModelFile(ScanActivity.this)); // 211210 - 골반후방경사 모델 불러오기!!!
        } catch (IOException e) {
            e.printStackTrace();
        }

        predictBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                reSet();

                String result = ""; // 결과들을 모아두는 String, 매번 누를 때 마다 초기화를 해 주지 않으면 계속 누적된다.

                int imageTensorIndex = 0;
                int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // 모델에 들어가는 이미지 형태를 불러온다.
                imageSizeX = imageShape[1]; // 이미지 형태의 X Size
                imageSizeY = imageShape[2]; // 이미지 형태의 Y Size
                DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType(); // 이미지 데이터 타입 가져오기

                int probabilityTensorIndex = 0;
                int[] probabilityShape = tflite.getOutputTensor(probabilityTensorIndex).shape(); // 결과에 대한 형태를 가져온다.
                DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType(); // 결과 데이터 타입을 가져온다.

                inputImageBuffer = new TensorImage(imageDataType); // input image 설정
                outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape,probabilityDataType); // Output 버퍼 설정
                probabilityProcessor = new TensorProcessor.Builder().add(getPostProcessorNormalizeOP()).build(); // 프로세서 설정

                inputImageBuffer = loadImage(bitmap); // input 이미지 버퍼 설정

                tflite.run(inputImageBuffer.getBuffer(),outputProbabilityBuffer.getBuffer().rewind()); // 모델 가동

                try {
                    labels = FileUtil.loadLabels(ScanActivity.this,"labels.txt"); // 거북목 모델의 라벨 불러오기
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Map<String,Float> labelsProbability = new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue(); // 예측값과, 라벨을 연결시켜서 Map의 형태로 만들어 준다.

                float maxValueinMap = (Collections.max(labelsProbability.values()));

                if(labelsProbability.get("normal") > labelsProbability.get("text")){ // 가능성이 더 높은 놈으로 예측결과 출력
                    result += "정상, ";
                    ResultArr[0] = 0;
                }
                else{
                    result += "거북목, ";
                    ResultArr[0] = 1;
                }

                // 두 번째 모델 설정 (모델을 늘려갈 때 여기서부터 아래로 복붙을 해서 순차적으로 진행하면 될 듯 하다.)
                // 이미지는 어차피 들어가는 형태가 같으니(동일한 이미지로 여러번 진단과정을 거치는 것이니) 다시 설정할 필요가 없음
                int probabilityTensorIndex2 = 0;
                int[] probabilityShape2 = ableModel.getOutputTensor(probabilityTensorIndex2).shape();
                DataType probabilityDataType2 = ableModel.getOutputTensor(probabilityTensorIndex2).dataType();


                outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape2,probabilityDataType2);
                probabilityProcessor = new TensorProcessor.Builder().add(getPostProcessorNormalizeOP()).build();

                ableModel.run(inputImageBuffer.getBuffer(),outputProbabilityBuffer.getBuffer().rewind()); // 두 번째 모델 가동

                try {
                    labels = FileUtil.loadLabels(ScanActivity.this,"labels_able_unable.txt"); // 두 번째 모델의 라벨 불러오기
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Map<String,Float> labelsProbability2 = new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();

                float maxValueinMap2 = (Collections.max(labelsProbability2.values()));

                if(labelsProbability2.get("able") > labelsProbability2.get("unable")){ // 라벨별 확률이 높은 놈을 예측 결과로 출력
                    // 다리 아래쪽까지 나온 전신 사진이면 골반 진단 가능으로
                    result += "골반진단 가능, ";
                    ResultArr[1] = 1;
                }
                else{
                    // 아니면 불가능으로 하고, 백엔드와 연결시에는 여기서 진단 과정이 끝나고 만약 거북목이라면 거북목에 대해서만 진단 결과를 내리는 방향으로?
                    // 골반 관련 진단을 원하면 전신 사진을 넣어 달라는 문구도 추가하면 좋을 듯? (팝업 메시지 또는 적당한 것으로)
                    result += "골반진단 불가능, ";
                    ResultArr[1] = 0; // 골반진단 가능에 해당하지 않음 211209
                    ResultArr[2] = 2;
                    ResultArr[3] = 2;
                }

                if(ResultArr[1] == 1){ // 211209 - Able 대신에 사용하였음 (Able은 삭제)
                    // 세 번째 모델 설정
                    int probabilityTensorIndex3 = 0;
                    int[] probabilityShape3 = APTmodel.getOutputTensor(probabilityTensorIndex3).shape();
                    DataType probabilityDataType3 = APTmodel.getOutputTensor(probabilityTensorIndex3).dataType();


                    outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape3,probabilityDataType3);
                    probabilityProcessor = new TensorProcessor.Builder().add(getPostProcessorNormalizeOP()).build();

                    APTmodel.run(inputImageBuffer.getBuffer(),outputProbabilityBuffer.getBuffer().rewind()); // 두 번째 모델 가동

                    try {
                        labels = FileUtil.loadLabels(com.example.body_care.ScanActivity.this,"APTlabels.txt"); // 두 번째 모델의 라벨 불러오기
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Map<String,Float> labelsProbability3 = new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                            .getMapWithFloatValue();

                    float maxValueinMap3 = (Collections.max(labelsProbability3.values()));

                    if(labelsProbability3.get("APT") > labelsProbability3.get("Normal")){ // 라벨별 확률이 높은 놈을 예측 결과로 출력
                        // 골반전방경사!
                        result += "골반전방경사, ";
                        ResultArr[2] = 1; // 골반전방경사에 해당
                        ResultArr[3] = 0; // 골반전방경사면 후방경사가 아니기에 아니라는 것을 추가 - 211212
                    }
                    else{
                        // 골반전방경사가 아님!
                        result += "골반전방경사 아님!, ";
                        ResultArr[2] = 0; // 골반전방경사에 해당하지 않음
                    }
                }
                else{
                    result +="골반까지 나오는 사진을 준비 해 주세요!!";
                }

                // 211210 - 후방경사 모델 감지 추가
                if(ResultArr[2] == 0){ // 211210 - 골반전방경사가 아닐때! 후방경사 감지를 시작하게 된다.
                    // 네 번째 모델 설정
                    int probabilityTensorIndex4 = 0;
                    int[] probabilityShape4 = PPTmodel.getOutputTensor(probabilityTensorIndex4).shape();
                    DataType probabilityDataType4 = PPTmodel.getOutputTensor(probabilityTensorIndex4).dataType();


                    outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape4,probabilityDataType4);
                    probabilityProcessor = new TensorProcessor.Builder().add(getPostProcessorNormalizeOP()).build();

                    PPTmodel.run(inputImageBuffer.getBuffer(),outputProbabilityBuffer.getBuffer().rewind()); // 네 번째 모델 가동

                    try {
                        labels = FileUtil.loadLabels(ScanActivity.this,"PPTlabels.txt"); // 네 번째 모델의 라벨 불러오기
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Map<String,Float> labelsProbability4 = new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                            .getMapWithFloatValue();

                    float maxValueinMap4 = (Collections.max(labelsProbability4.values()));

                    if(labelsProbability4.get("PPT") > labelsProbability4.get("Normal")){ // 라벨별 확률이 높은 놈을 예측 결과로 출력
                        // 골반후방경사!
                        result += "골반후방경사";
                        ResultArr[3] = 1; // 골반후방경사에 해당
                    }
                    else{
                        // 골반후방경사가 아님!
                        result += "골반후방경사 아님!";
                        ResultArr[3] = 0; // 골반후방경사에 해당하지 않음
                    }
                }


                showResults();
                txtResult.setText(result); // 결과 출력

            }
        });

    }

    private TensorImage loadImage(final Bitmap bitmap){

        inputImageBuffer.load(bitmap);
        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                .add(getPreProcessorNormalizeOP())
                .build();

        return imageProcessor.process(inputImageBuffer);

    }




    //load tflite Model - 거북목 여부를 판단 해 주는 모델 불러오기
    private MappedByteBuffer loadmodelfile(Activity activity) throws IOException{
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startoffset = fileDescriptor.getStartOffset();
        long declareLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset, declareLength);
    }

    // load Second Model - 골반진단 가능 여부를 판단 해 주는 모델을 불러온다.
    private MappedByteBuffer loadAbleModelFile(Activity activity) throws IOException{
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("ableModel.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startoffset2 = fileDescriptor.getStartOffset();
        long declareLength2 = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset2, declareLength2);
    }

    // load Third Model - 골반전방경사 여부를 판단 해 주는 모델을 불러온다.
    private MappedByteBuffer loadAPTModelFile(Activity activity) throws IOException{
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("modelA.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startoffset3 = fileDescriptor.getStartOffset();
        long declareLength3 = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset3, declareLength3);
    }

    // 211210 - load Fourth Model - 골반후방경사 여부를 판단 해 주는 모델을 불러온다.
    private MappedByteBuffer loadPPTModelFile(Activity activity) throws IOException{
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("modelP.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startoffset3 = fileDescriptor.getStartOffset();
        long declareLength3 = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startoffset3, declareLength3);
    }

    private TensorOperator getPreProcessorNormalizeOP(){
        return new NormalizeOp(IMAGE_MEAN,IMAGE_STD);
    }

    private TensorOperator getPostProcessorNormalizeOP(){
        return new NormalizeOp(PROBABILITY_MEAN,PROBABILITY_STD);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 0 && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();

            bitmap = (Bitmap) extras.get("data");
            if(bitmap != null)
                imgView.setImageBitmap(bitmap);


        }



        if(requestCode == 12 &&  resultCode == RESULT_OK && data != null){
            imgUri = data.getData();

            try{
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),imgUri);
                imgView.setImageBitmap(bitmap);
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }


    }

    // show Restult

    private void showResults(){ // 211210 - 골반측정가능여부 같이 추가, 통으로 복붙하시면 됩니다!

        text = (LinearLayout) findViewById(R.id.text);
        no_text = (LinearLayout) findViewById(R.id.no_text);
        front_pelvis = (LinearLayout) findViewById(R.id.front_pelvis);
        no_front = (LinearLayout) findViewById(R.id.no_front_pelvis);
        post_pelvis = (LinearLayout) findViewById(R.id.poster_pelvis);
        no_post = (LinearLayout) findViewById(R.id.no_poster_pelvis);


        if(ResultArr[0] == 1){ // 거북목에 해당
            text.setBackgroundColor(Color.rgb(255,0,0));
            sol = "거북목교정";
        }
        else if(ResultArr[0] == 0){
            no_text.setBackgroundColor(Color.rgb(0,0,255)); // 211210 - 아닐때는 파란색으로 설정
        }

        if(ResultArr[2] == 1){ // 전방경사
            front_pelvis.setBackgroundColor(Color.rgb(255,0,0));
            sol += " +전방경사교정";
        }
        else if(ResultArr[2] == 0){ // 전방경사 x
            no_front.setBackgroundColor(Color.rgb(0,0,255)); // 211210 - 아닐때는 파란색으로 설정
            sol += " -전방경사";
        }
        // 211210 - 후방경사 여부에 따른 색깔 변화 부분 추가
        if(ResultArr[3] == 1){ // 후방경사
            post_pelvis.setBackgroundColor(Color.rgb(255,0,0));
            sol += " +후방경사교정";
        }
        else if(ResultArr[3] == 0){ // 후방경사 x
            no_post.setBackgroundColor(Color.rgb(0,0,255)); // 211210 - 아닐때는 파란색으로 설정
            sol += " -후방경사";
        }

        btnSolution.setVisibility(View.VISIBLE);
    }

    private void reSet(){ // 211210 - 리셋인데 조건문을 넣어놓은 것 같아서 삭제하였음, 통으로 복붙하면 됩니다!

        text = (LinearLayout) findViewById(R.id.text);
        no_text = (LinearLayout) findViewById(R.id.no_text);
        front_pelvis = (LinearLayout) findViewById(R.id.front_pelvis);
        no_front = (LinearLayout) findViewById(R.id.no_front_pelvis);
        post_pelvis = (LinearLayout) findViewById(R.id.poster_pelvis);
        no_post = (LinearLayout) findViewById(R.id.no_poster_pelvis);


        text.setBackgroundColor(Color.WHITE); // 거북
        no_text.setBackgroundColor(Color.WHITE); // 거북아님
        front_pelvis.setBackgroundColor(Color.WHITE);
        no_front.setBackgroundColor(Color.WHITE);
        post_pelvis.setBackgroundColor(Color.WHITE);
        no_post.setBackgroundColor(Color.WHITE);
        sol = "";

    }


}


