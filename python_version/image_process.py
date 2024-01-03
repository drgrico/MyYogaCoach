import numpy as np
import json
import os

'''
# odd number: left side of the body; even number: right side of the body
[0],       # nose
[11, 12],  # shoulder
[13, 14],  # elbow
[15, 16],  # wrist
[23, 24],  # hip
[25, 26],  # knee
[27, 28],  # ankle
'''
Landmark_labels = [
    0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28
]

# Real-world 3-dimensional coordinates in meters, with the midpoint of the hips as the origin.


def three_yoga_pose_detection(current_world_landmarks):
    if is_pose_steady(current_world_landmarks):
        # use left side of the body to classify yoga posture
        dict_current = format_world_landmarks(current_world_landmarks)

        # program according to real personal pose experiments
        # y axis is vertically down in the image
        hip_middle_y = dict_current[23][1]+dict_current[24][1]
        wrist_middle_y = dict_current[15][1]+dict_current[16][1]
        shoulder_middle_y = dict_current[11][1]+dict_current[12][1]

        if wrist_middle_y > shoulder_middle_y > hip_middle_y:  # downdog
            print('downdog')
        elif hip_middle_y > shoulder_middle_y > wrist_middle_y:  # warrior
            print('warrior')
        elif wrist_middle_y > hip_middle_y > shoulder_middle_y:
            print('Plank')


def load_file_world_landmarks():
    if os.path.exists('last_frame.json'):
        with open('last_frame.json') as file1:
            dict1 = json.load(file1)
    else:
        dict1 = None

    return dict1


def format_world_landmarks(landmarks):
    if landmarks is None:
        return None

    dict1 = {}
    for label in Landmark_labels:
        x = landmarks.landmark[label].x
        y = landmarks.landmark[label].y
        z = landmarks.landmark[label].z
        dict1[int(label)] = [x, y, z]
    return dict1


def is_pose_steady(current_world_landmarks):

    dict_last_frame = load_file_world_landmarks()

    dict_current = format_world_landmarks(current_world_landmarks)
    if dict_current is None:
        return False

    with open('last_frame.json', 'w') as file1:
        json.dump(dict_current, file1)

    if dict_last_frame is None:
        return False

    deviation_threshold = 0.3  # this value can be changed !
    # 每一个位置的坐标偏差都不能大
    for key in Landmark_labels:
        list1 = list(map(lambda x, y: (x-y)/y,
                     dict_current[key], dict_last_frame[str(key)]))

        if np.std(list1) >= deviation_threshold:
            return False

    return True
